package com.arm.cmsis.pack.project;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.PlatformUI;

import com.arm.cmsis.pack.CpPlugIn;
import com.arm.cmsis.pack.ICpEnvironmentProvider;
import com.arm.cmsis.pack.ICpPackInstaller;
import com.arm.cmsis.pack.ICpPackManager;
import com.arm.cmsis.pack.build.IBuildSettings;
import com.arm.cmsis.pack.build.IMemorySettings;
import com.arm.cmsis.pack.build.settings.ILinkerScriptGenerator;
import com.arm.cmsis.pack.build.settings.IRteToolChainAdapter;
import com.arm.cmsis.pack.common.CmsisConstants;
import com.arm.cmsis.pack.configuration.IRteConfiguration;
import com.arm.cmsis.pack.configuration.RteConfiguration;
import com.arm.cmsis.pack.data.ICpFile;
import com.arm.cmsis.pack.data.ICpItem;
import com.arm.cmsis.pack.enums.EEvaluationResult;
import com.arm.cmsis.pack.enums.EFileCategory;
import com.arm.cmsis.pack.enums.EFileRole;
import com.arm.cmsis.pack.error.ICmsisConsole;
import com.arm.cmsis.pack.error.ICmsisConsoleStrategy;
import com.arm.cmsis.pack.generic.IAttributes;
import com.arm.cmsis.pack.info.ICpConfigurationInfo;
import com.arm.cmsis.pack.info.ICpDeviceInfo;
import com.arm.cmsis.pack.info.ICpFileInfo;
import com.arm.cmsis.pack.parser.CpConfigParser;
import com.arm.cmsis.pack.preferences.CpPreferenceInitializer;
import com.arm.cmsis.pack.project.ui.RteProjectDecorator;
import com.arm.cmsis.pack.project.utils.ProjectUtils;
import com.arm.cmsis.pack.rte.IRteModel;
import com.arm.cmsis.pack.rte.RteModelUtils;
import com.arm.cmsis.pack.rte.components.IRteComponentItem;
import com.arm.cmsis.pack.rte.dependencies.IRteDependencyItem;
import com.arm.cmsis.pack.ui.CpPlugInUI;
import com.arm.cmsis.pack.ui.console.RteConsole;
import com.arm.cmsis.pack.utils.Utils;

public class RteProjectUpdater extends WorkspaceJob implements ICmsisConsoleStrategy {

    public static final String RTE_PROBLEM_MARKER = CpPlugInUI.RTE_PROBLEM_MARKER;

    public static final int LOAD_CONFIGS = 0x01;
    public static final int UPDATE_TOOLCHAIN = 0x02; // forces update of all relevant toolchain settings
    public static final int CLEANUP_RTE_FILES = 0x04; // delete excluded RTE config files
    public static final int SUPRESS_INFO_MESSAGES = 0x08; // suppresses info messages (useful when called during import)

    // cause flags
    public static final int CAUSE_PROJECT_CREATED = 0x10;
    public static final int CAUSE_PROJECT_LOAD = 0x20;
    public static final int CAUSE_PROJECT_RESET = 0x40;
    public static final int CAUSE_CONFIG_CHANGED = 0x80;
    public static final int CAUSE_PACKS_CHANGED = 0x100;
    public static final int CAUSE_GPDSC_CHANGED = 0x200;
    public static final int CAUSE_IMPORT_COMPLETED = 0x400;

    protected IRteProject rteProject;
    protected IProject project;
    protected IProgressMonitor monitor = null;

    protected int updateFlags = 0;
    protected boolean bLoadConfigs = false;
    protected boolean bForceUpdateToolchain = false;
    protected boolean bSaveProject = false;
    protected boolean bDeleteConfigFiles = false;
    protected boolean bSuppressInfoMessages = false;
    protected ICmsisConsole fCmsisConsole = null;

    protected RteProjectStorage projectStorage = null;
    protected IRteToolChainAdapter toolChainAdapter = null;
    protected IAttributes rteOptionsFromToolchain = null;
    IManagedBuildInfo buildInfo = null;

    public RteProjectUpdater(IRteProject rteProject, int updateFlags) {
        super("RTE Project Updater"); //$NON-NLS-1$
        this.rteProject = rteProject;
        this.project = rteProject.getProject();
        this.updateFlags = updateFlags;
        bLoadConfigs = (updateFlags & LOAD_CONFIGS) == LOAD_CONFIGS;
        bForceUpdateToolchain = (updateFlags & UPDATE_TOOLCHAIN) == UPDATE_TOOLCHAIN;
        bDeleteConfigFiles = (updateFlags & CLEANUP_RTE_FILES) == CLEANUP_RTE_FILES;
        bSuppressInfoMessages = (updateFlags & SUPRESS_INFO_MESSAGES) == SUPRESS_INFO_MESSAGES;
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        setRule(workspace.getRoot()); // ensures synch update
    }

    @Override
    public ICmsisConsole getCmsisConsole() {
        if (fCmsisConsole == null) {
            fCmsisConsole = createDefaultCmsisConsole();
        }
        return fCmsisConsole;
    }

    @Override
    public void setCmsisConsole(ICmsisConsole console) {
        fCmsisConsole = console;
    }

    /**
     * Returns the RteConsole, creates if not yet initialized
     *
     * @return RteConsole
     */
    @Override
    public ICmsisConsole createDefaultCmsisConsole() {
        return RteConsole.openConsole(project);
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) {
        if (project == null || rteProject == null) {
            return new Status(IStatus.ERROR, CpPlugInUI.PLUGIN_ID, Messages.RteProjectUpdater_ErrorProjectIsNull);
        }

        this.monitor = monitor;
        bSaveProject = false;
        Status status = null;
        EEvaluationResult res = EEvaluationResult.FULFILLED;
        try {
            outputStartUpdateMessage();

            String packRoot = CpVariableResolver.getCmsisPackRoot();
            if (packRoot == null || packRoot.isEmpty()) {
                status = new Status(IStatus.WARNING, CpPlugInUI.PLUGIN_ID,
                        Messages.RteProjectUpdater_ErrorCmisPackRootNotSet);
                throw new CoreException(status);
            }

            buildInfo = ManagedBuildManager.getBuildInfo(project);
            if (buildInfo == null) {
                if (project.hasNature("org.eclipse.cdt.managedbuilder.core.managedBuildNature")) { //$NON-NLS-1$
                    status = new Status(IStatus.ERROR, CpPlugInUI.PLUGIN_ID,
                            "Error Updating RTE project : CDT build information is not loaded, try to restart Eclipse"); //$NON-NLS-1$
                    return status;
                }
            }

            projectStorage = rteProject.getProjectStorage();
            if (projectStorage != null) {
                // obtain toolchain adaper its RTE options from active configuration
                toolChainAdapter = projectStorage.getToolChainAdapter();
                if (toolChainAdapter != null) {
                    IConfiguration activeConfig = ProjectUtils.getDefaultConfiguration(project);
                    rteOptionsFromToolchain = toolChainAdapter.getRteOptions(activeConfig);
                }
            }

            if (bLoadConfigs) {
                res = loadConfigFile();
            }
            addResources();
            removeResources();

            updateGeneratedHeaders();

            updateBuildSettings(bForceUpdateToolchain);

            if (bSaveProject) {
                rteProject.save();
            }
            project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            updateIndex();

        } catch (CoreException e) {
            status = new Status(e.getStatus().getSeverity(), CpPlugInUI.PLUGIN_ID,
                    Messages.RteProjectUpdater_ErrorUpdatingRteProject, e);
            res = EEvaluationResult.FAILED;
        } catch (Exception e) {
            e.printStackTrace();
            status = new Status(IStatus.ERROR, CpPlugInUI.PLUGIN_ID, Messages.RteProjectUpdater_ErrorUpdatingRteProject,
                    e);
            res = EEvaluationResult.FAILED;
        }

        // Output the error message to the RTE console
        if (res.ordinal() < EEvaluationResult.INSTALLED.ordinal() || status != null) {
            getCmsisConsole().outputError(Messages.RteProjectUpdater_Fail);
            if (status != null) {
                String msg = status.getMessage();
                Throwable e = status.getException();
                if (e != null && e.getMessage() != null) {
                    msg += ": " + e.getLocalizedMessage(); //$NON-NLS-1$
                }

                getCmsisConsole().outputInfo(msg);
                IStatus[] statusArray = status.getChildren();
                if (statusArray != null && statusArray.length > 0) {
                    for (IStatus s : statusArray) {
                        getCmsisConsole().outputInfo(s.getMessage());
                    }
                }
            }
        } else if (!bSuppressInfoMessages) {
            getCmsisConsole().outputInfo(Messages.RteProjectUpdater_Success);

        }
        if (!bSuppressInfoMessages) {
            getCmsisConsole().output(CmsisConstants.EMPTY_STRING);
        }

        RteProjectDecorator.refresh();
        if (status == null) {
            status = new Status(IStatus.OK, CpPlugInUI.PLUGIN_ID, Messages.RteProjectUpdater_ProjectUpdated);
        }

        rteProject.setUpdateCompleted(true);

        return status;
    }

    protected void outputStartUpdateMessage() {
        if (bSuppressInfoMessages) {
            return;
        }
        String timestamp = Utils.getCurrentTimeStamp();
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append(" **** "); //$NON-NLS-1$
        sb.append(Messages.RteProjectUpdater_UpdatingProject).append(CmsisConstants.SPACE).append(project.getName());
        sb.append(": ").append(getCauseMessage()); //$NON-NLS-1$
        getCmsisConsole().outputInfo(sb.toString());
    }

    protected String getCauseMessage() {
        if ((updateFlags & CAUSE_PROJECT_LOAD) == CAUSE_PROJECT_LOAD) {
            return Messages.RteProjectUpdater_InitialProjectLoad;
        }
        if ((updateFlags & CAUSE_PROJECT_RESET) == CAUSE_PROJECT_RESET) {
            return Messages.RteProjectUpdater_ProjectReset;
        }
        if ((updateFlags & CAUSE_PROJECT_CREATED) == CAUSE_PROJECT_CREATED) {
            return Messages.RteProjectUpdater_ProjectCreated;
        }

        if ((updateFlags & CAUSE_CONFIG_CHANGED) == CAUSE_CONFIG_CHANGED) {
            return Messages.RteProjectUpdater_ConfigChanged + " [" + rteProject.getRteConfigurationName() + "] " //$NON-NLS-1$ //$NON-NLS-2$
                    + Messages.RteProjectUpdater_isSaved;
        }
        if ((updateFlags & CAUSE_PACKS_CHANGED) == CAUSE_PACKS_CHANGED) {
            return Messages.RteProjectUpdater_PacksInstalled;
        }
        if ((updateFlags & CAUSE_IMPORT_COMPLETED) == CAUSE_IMPORT_COMPLETED) {
            return Messages.RteProjectUpdater_ImportCompleted;
        }

        if ((updateFlags & CAUSE_GPDSC_CHANGED) == CAUSE_GPDSC_CHANGED) {
            Collection<String> gpdscFiles = rteProject.getRteConfiguration().getGeneratedPackNames();
            if (!gpdscFiles.isEmpty()) {
                // it is most probable that only one file is used, however output all in the
                // collection
                return Messages.RteProjectUpdater_GpdscChanged + CmsisConstants.SPACE + gpdscFiles.toString()
                        + CmsisConstants.SPACE + Messages.RteProjectUpdater_isChanged;
            }
        }

        return Messages.RteProjectUpdater_ProjectRefresh;

    }

    protected void collectErrors(Collection<? extends IRteDependencyItem> errors) throws CoreException {
        IFile rteFile = rteProject.getProject().getFile(rteProject.getName() + CmsisConstants.DOT_RTECONFIG);
        rteFile.deleteMarkers(RTE_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
        if (!rteFile.exists()) {
            return;
        }

        if (errors == null || errors.isEmpty()) {
            return;
        }

        IRteConfiguration conf = rteProject.getRteConfiguration();
        if (conf == null) {
            return;
        }

        for (IRteDependencyItem depItem : errors) {
            IMarker marker = rteFile.createMarker(RTE_PROBLEM_MARKER);
            Map<String, Object> attributes = new HashMap<>();
            attributes.put(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
            attributes.put(IMarker.MESSAGE, depItem.getName() + " - " + depItem.getDescription()); //$NON-NLS-1$
            IRteComponentItem item = depItem.getComponentItem();
            if (item != null) {
                attributes.put(IMarker.LOCATION, String.join("->", //$NON-NLS-1$
                        item.getKeyPath().stream().filter(s -> !s.isEmpty()).collect(Collectors.toList())));
                attributes.put(CpPlugInUI.RTE_PROBLEM_MARKER_DEP_ITEM, depItem);
            }
            marker.setAttributes(attributes);
        }
    }

    protected void updateIndex() {
        rteProject.setUpdateCompleted(true);
        CpProjectPlugIn.getRteProjectManager().updateIndex(project);
    }

    protected EEvaluationResult loadConfigFile() throws CoreException {
        String savedRteConfigName = rteProject.getRteConfigurationName();
        IRteConfiguration rteConf = loadRteConfiguration(savedRteConfigName);
        Collection<? extends IRteDependencyItem> errors = rteConf.validate();

        collectErrors(errors);

        EEvaluationResult res = rteConf.getEvaluationResult();

        if (errors == null || errors.isEmpty()) {
            return res;
        }
        String msg = Messages.RteProjectUpdater_ErrorLoadinConfigFile + " '" + savedRteConfigName + "':"; //$NON-NLS-1$ //$NON-NLS-2$
        getCmsisConsole().outputError(msg);
        for (IRteDependencyItem item : errors) {
            String s = item.getName() + " - " + item.getDescription(); //$NON-NLS-1$
            getCmsisConsole().output(s);
            msg += System.lineSeparator() + s;
        }

        installMissingPacks(rteConf.getRteModel());

        if (res.ordinal() > EEvaluationResult.FAILED.ordinal()) {
            return res;
        }
        Status status = new Status(IStatus.WARNING, CpPlugInUI.PLUGIN_ID, msg);
        throw new CoreException(status);
    }

    /**
     * Triggers installation of missing packs
     *
     * @param rteModel IRteModel to query for missing packs
     */
    protected void installMissingPacks(IRteModel rteModel) {
        if (!PlatformUI.isWorkbenchRunning()) {
            return; // no implicit pack install in headless mode
        }

        if (!CpPreferenceInitializer.isAutoInstallMissingPacks())
            return;

        if (rteProject == null || !rteProject.isInstallMissingPacksOnUpdate()) {
            return;
        }
        if (rteModel == null)
            return;
        ICpPackManager pm = CpPlugIn.getPackManager();
        ICpPackInstaller packInstaller = pm.getPackInstaller();
        if (packInstaller == null)
            return;

        rteProject.setInstallMissingPacksOnUpdate(false); // only once
        pm.ensureAllPacksLoaded();
        packInstaller.installPacks(RteModelUtils.getMissingPacks(rteModel));
    }

    protected IRteConfiguration loadRteConfiguration(String savedRteConfigName) throws CoreException {
        if (savedRteConfigName == null || savedRteConfigName.isEmpty()) {
            String msg = Messages.RteProjectUpdater_ErrorLoadinConfigFile + " '' " + //$NON-NLS-1$
                    Messages.RteProjectUpdater_ErrorConfigFileNotExist;
            Status status = new Status(IStatus.ERROR, CpPlugInUI.PLUGIN_ID, msg);
            throw new CoreException(status);
        }

        String rteConfigName = project.getName() + CmsisConstants.DOT_RTECONFIG;
        // moving project can still left the old file
        IFile iFile = project.getFile(rteConfigName);
        if (!iFile.exists() || iFile.getLocation() == null) {
            // ensure file has the project name (e.g. after rename)
            if (!rteConfigName.equalsIgnoreCase(savedRteConfigName)) {
                iFile = project.getFile(savedRteConfigName);
                if (!iFile.exists() || iFile.getLocation() == null) {
                    String msg = Messages.RteProjectUpdater_ErrorLoadinConfigFile + " '" + savedRteConfigName + "' " + //$NON-NLS-1$//$NON-NLS-2$
                            Messages.RteProjectUpdater_ErrorConfigFileNotExist;
                    Status status = new Status(IStatus.ERROR, CpPlugInUI.PLUGIN_ID, msg);
                    throw new CoreException(status);
                }
                rteConfigName = savedRteConfigName;
            }
        }

        File file = iFile.getLocation().toFile();
        CpConfigParser confParser = new CpConfigParser();
        ICpItem root = confParser.parseFile(file.getAbsolutePath());
        IRteConfiguration rteConf = null;
        if (root instanceof ICpConfigurationInfo) {
            ICpConfigurationInfo info = (ICpConfigurationInfo) root;
            // adjust RTE options
            ICpItem toolChainInfo = info.getToolChainInfo();
            if (rteOptionsFromToolchain != null && toolChainInfo != null) {
                toolChainInfo.attributes().addAttributes(rteOptionsFromToolchain);
            }
            rteConf = new RteConfiguration();
            rteConf.setConfigurationInfo(info);
            rteProject.setRteConfiguration(rteConfigName, rteConf);

            // finally update project storage and rename file if needed
            if (!rteConfigName.equalsIgnoreCase(savedRteConfigName)) {
                rteProject.setRteConfigurationName(rteConfigName);
                bSaveProject = true;
            }
        } else {
            String msg = Messages.RteProjectUpdater_ErrorLoadinConfigFile + " '" + rteConfigName + "' " + //$NON-NLS-1$//$NON-NLS-2$
                    Messages.RteProjectUpdater_ErrorParsingFailed;
            Status status = new Status(IStatus.ERROR, CpPlugInUI.PLUGIN_ID, msg);
            throw new CoreException(status);
        }

        return rteConf;
    }

    /**
     * Removes resources that are no longer belong to project and refreshes
     * remaining ones
     *
     * @throws CoreException
     */
    protected void removeResources() throws CoreException {
        IResource rteFolder = project.findMember(CmsisConstants.RTE);
        removeResources(rteFolder);
    }

    protected void removeResources(IResource res) throws CoreException {
        if (res == null) {
            return;
        }
        int type = res.getType();
        if (type == IResource.FILE) {
            removeFileResource((IFile) res);
        } else if (res.getType() == IResource.FOLDER) {
            removeFolderResource((IFolder) res);
        }
    }

    protected void removeFileResource(IFile res) throws CoreException {
        IPath path = res.getProjectRelativePath();
        String dstFile = path.toString();

        if (!isFileUsed(res)) {
            if (res.isLinked()) {
                res.delete(IResource.FORCE, monitor);
            } else if (bDeleteConfigFiles) {
                res.delete(IResource.FORCE | IResource.KEEP_HISTORY, monitor);
            } else {
                ProjectUtils.setExcludeFromBuild(project, dstFile, true);
            }
        } else if (ProjectUtils.isExcludedFromBuild(project, dstFile)) {
            ProjectUtils.setExcludeFromBuild(project, dstFile, false);
        }
    }

    protected void removeFolderResource(IFolder folder) throws CoreException {
        IResource[] members = folder.members();
        for (IResource r : members) {
            removeResources(r);
        }
        folder.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        if (!folder.getName().equals(CmsisConstants.RTE) && folder.members().length == 0) {
            folder.delete(true, true, null);
        }
    }

    protected boolean isFileUsed(IResource res) {
        IPath path = res.getProjectRelativePath();
        String ext = path.getFileExtension();
        if (CmsisConstants.GPDSC_TAG.equals(ext)) {
            IPath gpdsc = res.getLocation();
            IRteConfiguration rteConf = rteProject.getRteConfiguration();
            return rteConf.isGeneratedPackUsed(gpdsc.toString());
        }
        return rteProject.isFileUsed(path.toString());
    }

    protected void addResources() throws CoreException {
        IRteConfiguration rteConf = rteProject.getRteConfiguration();
        addResources(rteConf);
        project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
    }

    protected void addResources(IRteConfiguration rteConf) throws CoreException {
        if (rteConf == null) {
            return;
        }

        Map<String, ICpFileInfo> fileMap = rteConf.getProjectFiles();
        for (Entry<String, ICpFileInfo> e : fileMap.entrySet()) {
            String projectRelativePath = e.getKey();
            ICpFileInfo fi = e.getValue();
            addFile(rteConf, projectRelativePath, fi);
        }
    }

    protected void addFile(IRteConfiguration rteConf, String dstFile, ICpFileInfo fi) throws CoreException {
        ICpFile f = fi.getFile();
        if (f == null) {
            return;
        }
        String srcFile = f.getAbsolutePath(f.getName());
        if (srcFile == null) {
            return;
        }

        boolean generated = f.isGenerated();
        ICpConfigurationInfo cpConf = rteConf.getConfigurationInfo();
        String base = cpConf.getDir(true);
        boolean local = srcFile.startsWith(base);
        EFileRole role = fi.getRole();
        if (generated || local) {
            role = EFileRole.NONE; // prevent generated files from copy
        }

        if (role == EFileRole.CONFIG) {
            int index = -1;
            EFileCategory cat = fi.getCategory();
            if (cat.isHeader() || cat.isSource()) {
                String baseSrc = Utils.extractBaseFileName(srcFile);
                String baseDst = Utils.extractBaseFileName(dstFile);
                int len = baseSrc.length() + 1;
                if (baseDst.length() > len) {
                    String instance = baseDst.substring(len);
                    try {
                        index = Integer.decode(instance);
                    } catch (NumberFormatException e) {
                        // do nothing, use -1
                    }
                }
            }
            int bCopied = ProjectUtils.copyFile(project, srcFile, dstFile, index, monitor, false);
            if (bCopied == 1) {
                updateFileVersion(dstFile, fi.getVersion(), true);
            } else if (bCopied == -1) {
                String savedVersion = getFileVersion(dstFile);
                if (savedVersion != null) {
                    fi.setVersion(savedVersion);
                }
            }

        } else if (role == EFileRole.COPY) {
            int bCopied = ProjectUtils.copyFile(project, srcFile, dstFile, -1, monitor, false);
            if (bCopied == 1) {
                updateFileVersion(dstFile, fi.getVersion(), true);
            } else if (bCopied == -1) {
                String savedVersion = getFileVersion(dstFile);
                if (savedVersion != null) {
                    fi.setVersion(savedVersion);
                }
            }
        } else if (!local) {
            srcFile = CpVariableResolver.insertCmsisRootVariable(srcFile);
            if (srcFile != null) {
                if (srcFile.startsWith(CmsisConstants.CMSIS_RTE_VAR)) {
                    // remove prefix variable since resulted URL cannot be created in case of an
                    // absolute path
                    srcFile = srcFile.substring(CmsisConstants.CMSIS_RTE_VAR.length());
                }
                ProjectUtils.createLink(project, srcFile, dstFile, monitor);
            }
        }

        if (ProjectUtils.isExcludedFromBuild(project, dstFile)) {
            ProjectUtils.setExcludeFromBuild(project, dstFile, false);
        }

    }

    public void updateFileVersion(String projectRelativePath, String version, boolean bForce) {
        RteProjectStorage projectStorage = rteProject.getProjectStorage();
        if (bForce || projectStorage.getConfigFileVersion(projectRelativePath) == null) {
            projectStorage.setConfigFileVersion(projectRelativePath, version);
            bSaveProject = true;
        }
    }

    public String getFileVersion(String projectRelativePath) {
        RteProjectStorage projectStorage = rteProject.getProjectStorage();
        return projectStorage.getConfigFileVersion(projectRelativePath);
    }

    protected void updateGeneratedHeaders() throws CoreException {
        IRteConfiguration rteConf = rteProject.getRteConfiguration();
        if (rteConf == null) {
            return;
        }
        updateRteComponentsH();
        updateRteHeaderFile(CmsisConstants.Pre_Include_Global_h, rteConf.getGlobalPreIncludeStrings());
        Map<String, String> locals = rteConf.getLocalPreIncludeStrings();
        for (Entry<String, String> entry : locals.entrySet()) {
            updateRteHeaderFile(entry.getKey(), Arrays.asList(entry.getValue()));
        }
    }

    protected void updateRteComponentsH() throws CoreException {
        IRteConfiguration rteConf = rteProject.getRteConfiguration();
        if (rteConf == null) {
            return;
        }
        List<String> content = new LinkedList<>();
        // prepend #define CMSIS_device_header
        String deviceHeader = rteConf.getDeviceHeader();
        if (deviceHeader != null && !deviceHeader.isEmpty()) {
            String s = "#define " + CmsisConstants.CMSIS_device_header + " \"" + deviceHeader + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            content.add("/*"); //$NON-NLS-1$
            content.add(" * Define the Device Header File:"); //$NON-NLS-1$
            content.add("*/"); //$NON-NLS-1$
            content.add(s);
            content.add(CmsisConstants.EMPTY_STRING);
        }

        Collection<String> code = rteConf.getRteComponentsHCode();
        if (code != null) {
            content.addAll(code);
        }
        updateRteHeaderFile(CmsisConstants.RTE_Components_h, content);
    }

    protected void updateRteHeaderFile(String headerName, Collection<String> code) throws CoreException {
        if (code == null || code.isEmpty())
            return;
        // ensure resource exists
        try {
            String headerPath = CmsisConstants.RTE + '/' + headerName;
            String header_h = Utils.nonAlnumToUndersore(headerName).toUpperCase();
            IFile f = ProjectUtils.createFile(project, headerPath, monitor);
            IPath p = f.getLocation();
            p.toFile().setWritable(true);
            PrintWriter pw = new PrintWriter(p.toOSString());
            writeRteComponentsHhead(pw, header_h);
            writeRteComponentsHbody(pw, code);
            writeRteComponentsHtail(pw, header_h);
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    protected void writeRteComponentsHbody(PrintWriter pw, Collection<String> code) {
        if (code == null || code.isEmpty()) {
            return;
        }
        for (String s : code) {
            pw.println(s);
        }
    }

    protected void writeRteComponentsHhead(PrintWriter pw, String header_h) {

        pw.println("/*"); //$NON-NLS-1$
        pw.println(" * Auto generated Run-Time-Environment Component Configuration File"); //$NON-NLS-1$
        pw.println(" *      *** Do not modify ! ***"); //$NON-NLS-1$
        pw.println(" *"); //$NON-NLS-1$
        pw.println(" * Project: " + rteProject.getName()); //$NON-NLS-1$
        pw.print(" * RTE configuration: "); //$NON-NLS-1$
        pw.println(rteProject.getRteConfigurationName());
        pw.println("*/"); //$NON-NLS-1$

        pw.println("#ifndef " + header_h); //$NON-NLS-1$
        pw.println("#define " + header_h); //$NON-NLS-1$
        pw.println();
    }

    protected void writeRteComponentsHtail(PrintWriter pw, String header_h) {
        pw.println();
        pw.println("#endif /* " + header_h + " */"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    protected void updateBuildSettings(boolean bForceUpdateToolchain) {
        if (projectStorage == null || toolChainAdapter == null)
            return;
        ICpEnvironmentProvider envProvider = CpPlugIn.getEnvironmentProvider();
        IAttributes deviceAttributes = projectStorage.getDeviceAttributes();
        IRteConfiguration rteConfig = rteProject.getRteConfiguration();
        ICpConfigurationInfo configInfo = rteConfig.getConfigurationInfo();
        ICpDeviceInfo deviceInfo = rteConfig.getDeviceInfo();
        IBuildSettings buildSettings = rteConfig.getBuildSettings();

        boolean bInit = deviceInfo != null && !deviceInfo.attributes().matchCommonAttributes(deviceAttributes);
        if (bInit || bForceUpdateToolchain) {
            bSaveProject = true;
            projectStorage.setDeviceInfo(deviceInfo);
            String linkerScriptFile = buildSettings.getSingleLinkerScriptFile();
            if (linkerScriptFile == null) {
                ILinkerScriptGenerator lsGen = toolChainAdapter.getLinkerScriptGenerator();
                if (lsGen != null) {
                    linkerScriptFile = getLinkerScriptFile(lsGen);
                    try {
                        IMemorySettings memorySettings = rteConfig.getMemorySettings();
                        String script = lsGen.generate(memorySettings);
                        if (script != null && !script.isEmpty()) {
                            writeLinkerScriptFile(linkerScriptFile, script);
                            buildSettings.addStringListValue(IBuildSettings.RTE_LINKER_SCRIPT,
                                    CmsisConstants.PROJECT_LOCAL_PATH + linkerScriptFile);
                        }
                    } catch (CoreException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (buildInfo == null) {
            return;
        }
        String[] configNames = buildInfo.getConfigurationNames();
        for (String name : configNames) {
            IConfiguration config = ProjectUtils.getConfiguration(project, name);
            if (bInit || bForceUpdateToolchain) {
                envProvider.adjustInitialBuildSettings(buildSettings, configInfo);
                toolChainAdapter.setInitialToolChainOptions(config, buildSettings);
            } else {
                envProvider.adjustBuildSettings(buildSettings, configInfo);
                toolChainAdapter.setToolChainOptions(config, buildSettings);
            }

        }
        ManagedBuildManager.saveBuildInfo(project, true);
    }

    protected String getLinkerScriptFile(ILinkerScriptGenerator lsGen) {
        IRteConfiguration rteConfiguration = rteProject.getRteConfiguration();
        String deviceName = rteConfiguration.getDeviceInfo().getFullDeviceName();
        return CmsisConstants.CMSIS_RTE + CmsisConstants.UNDERSCORE + Utils.wildCardsToX(deviceName)
                + CmsisConstants.DOT + lsGen.getFileExtension();
    }

    protected void writeLinkerScriptFile(String fileName, String script) {
        if (script == null || script.isEmpty()) {
            return;
        }

        try {
            IFile file = ProjectUtils.createFile(project, fileName, monitor);
            IPath loc = file.getLocation();
            File f = loc.toFile();
            if (f != null && f.exists()) {
                return; // destination file already exists
            }

            String osPath = loc.toOSString();
            PrintWriter pw = new PrintWriter(osPath);
            pw.write(script);
            pw.close();
        } catch (CoreException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
