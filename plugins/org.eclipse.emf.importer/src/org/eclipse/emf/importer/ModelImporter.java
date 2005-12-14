/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: ModelImporter.java,v 1.22 2005/12/14 07:48:49 marcelop Exp $
 */
package org.eclipse.emf.importer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.emf.codegen.ecore.Generator;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.util.CodeGenUtil;
import org.eclipse.emf.common.CommonPlugin;
import org.eclipse.emf.common.util.AbstractTreeIterator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.DiagnosticException;
import org.eclipse.emf.common.util.Monitor;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.UniqueEList;
import org.eclipse.emf.converter.ConverterPlugin;
import org.eclipse.emf.converter.ModelConverter;
import org.eclipse.emf.converter.util.ConverterUtil;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.importer.util.ImporterUtil;


/**
 * @since 2.1.0
 */
public abstract class ModelImporter extends ModelConverter
{
  public static class EPackageImportInfo extends ModelConverter.EPackageConvertInfo
  {
    protected String basePackage;
    protected String prefix;

    public String getBasePackage()
    {
      return basePackage;
    }

    public void setBasePackage(String basePackage)
    {
      this.basePackage = basePackage;
    }

    public String getEcoreFileName()
    {
      return getConvertData();
    }

    public void setEcoreFileName(String ecoreFileName)
    {
      setConvertData(ecoreFileName);
    }

    public String getPrefix()
    {
      return prefix;
    }

    public void setPrefix(String prefix)
    {
      this.prefix = prefix;
    }
  }
  
  protected List fileExtensions;

  protected IPath originalGenModelPath;
  protected GenModel originalGenModel;

  protected IPath genModelProjectLocation;
  protected IPath genModelContainerPath;
  protected String genModelFileName;
  protected IPath genModelPath;

  protected List modelLocationURIs;
  protected String modelLocation;

  protected String modelPluginID;
  protected String modelPluginDirectory;

  protected boolean usePlatformURI = true;
  protected IWorkspaceRoot workspaceRoot;
  
  public void dispose()
  {
    originalGenModel = null;
    workspaceRoot = null;

    super.dispose();
  }

  public List getFileExtensions()
  {
    if (fileExtensions == null)
    {
      fileExtensions = new ArrayList();
    }
    return fileExtensions;
  }

  public boolean usePlatformURI()
  {
    return usePlatformURI;
  }

  public void setUsePlatformURI(boolean usePlatformURI)
  {
    this.usePlatformURI = usePlatformURI;
  }

  public void defineOriginalGenModelPath(IPath path) throws DiagnosticException
  {
    if (getOriginalGenModelPath() == null)
    {
      originalGenModelPath = path;
      if (getOriginalGenModelPath() != null)
      {
        URI genModelURI = createFileURI(getOriginalGenModelPath().toString());
        loadOriginalGenModel(genModelURI);
      }
    }
  }

  public IPath getOriginalGenModelPath()
  {
    return originalGenModelPath;
  }

  protected List computeEPackagesBeingReloaded()
  {
    if (getOriginalGenModel() != null)
    {
      List ePackages = new ConverterUtil.EPackageList();
      for (Iterator i = getOriginalGenModel().getGenPackages().iterator(); i.hasNext();)
      {
        GenPackage genPackage = (GenPackage)i.next();
        EPackage originalEPackage = genPackage.getEcorePackage();
        String nsURI = originalEPackage.getNsURI();
        if (nsURI != null)
        {
          for (Iterator j = getEPackages().iterator(); j.hasNext();)
          {
            EPackage ePackage = (EPackage)j.next();
            if (nsURI.equals(ePackage.getNsURI()))
            {
              ePackages.add(ePackage);
              break;
            }
          }
        }
      }
      return ePackages;
    }
    else
    {
      return Collections.EMPTY_LIST;
    }
  }

  public void setGenModelFileName(String name)
  {
    genModelFileName = name == null || name.trim().length() == 0 ? null : name;
    genModelPath = null;
  }

  public String getGenModelFileName()
  {
    return genModelFileName;
  }
  
  public String computeDefaultGenModelFileName()
  {
    List modelLocationURIs = getModelLocationURIs();
    if (!modelLocationURIs.isEmpty())
    {
      URI modelURI = (URI)modelLocationURIs.get(0);
      String name = URI.decode(modelURI.lastSegment());
      int index = name.lastIndexOf('.');
      return (index >= 0 ? name.substring(0, index) : name) + ".genmodel";
    }
    return null;
  }

  public Diagnostic checkGenModelFileName()
  {
    String message = null;
    String name = getGenModelFileName();
    if (name == null || name.trim().length() == 0)
    {
      message = ImporterPlugin.INSTANCE.getString("_UI_GeneratorModelFileNameCannotBeEmpty_message");
    }
    else if (!name.endsWith(".genmodel"))
    {
      message = ImporterPlugin.INSTANCE.getString("_UI_GeneratorModelFileNameMustEndWithGenModel_message");
    }

    if (message == null)
    {
      return Diagnostic.OK_INSTANCE;
    }
    else
    {
      return new BasicDiagnostic(Diagnostic.ERROR, ConverterPlugin.ID, ConverterUtil.ACTION_DEFAULT, message, null);
    }
  }

  public Diagnostic checkEcoreModelFileName(String fileName, String packageName)
  {
    String message = null;

    if (fileName == null || fileName.equals(""))
    {
      message = packageName == null ?
        ImporterPlugin.INSTANCE.getString("_UI_EcoreModelFileNameCannotBeEmpty_message") :
        ImporterPlugin.INSTANCE.getString("_UI_EcoreModelFileNameForPackageCannotBeEmpty_message", new Object []{ packageName });
    }
    else if (!fileName.endsWith(".ecore"))
    {
      message = packageName == null ?
        ImporterPlugin.INSTANCE.getString("_UI_EcoreModelFileNameMustEndWithEcore_message") :
        ImporterPlugin.INSTANCE.getString("_UI_EcoreModelFileNameForPackageMustEndWithEcore_message", new Object []{ packageName });
    }
    if (message == null)
    {
      return Diagnostic.OK_INSTANCE;
    }
    else
    {
      return new BasicDiagnostic(Diagnostic.ERROR, ConverterPlugin.ID, ConverterUtil.ACTION_DEFAULT, message, null);
    }
  }

  public void setGenModelProjectLocation(IPath genModelProjectLocation)
  {
    this.genModelProjectLocation = genModelProjectLocation;
  }

  public IPath getGenModelProjectLocation()
  {
    return genModelProjectLocation;
  }
  
  public void setGenModelContainerPath(IPath path)
  {
    genModelContainerPath = path;
    genModelPath = null;
  }

  public IPath getGenModelContainerPath()
  {
    return genModelContainerPath;
  }
  
  public IPath computeGenModelContainerPath(IPath projectPath)
  {
    return projectPath == null ? null : projectPath.append(getGenModelDefaultFolderPath());
  }

  protected IPath getGenModelDefaultFolderPath()
  {
    return new Path("model");
  }

  public IPath getGenModelPath()
  {
    if (genModelPath == null && getGenModelFileName() != null && getGenModelContainerPath() != null)
    {
      genModelPath = getGenModelContainerPath().append(getGenModelFileName());
    }
    return genModelPath;
  }

  public GenModel getGenModel()
  {
    if (genModel == null)
    {
      genModel = GenModelFactory.eINSTANCE.createGenModel();
      genModel.setImporterID(getID());
    }
    return genModel;
  }

  public boolean addGenModelToResource(boolean replace)
  {
    GenModel genModel = getGenModel();
    if (replace || genModel.eResource() == null)
    {
      IPath genModelPath = getGenModelPath();
      if (genModelPath != null)
      {
        URI genModelURI = createFileURI(genModelPath.toString());
        Resource genModelResource = createResourceSet().createResource(genModelURI);
        genModelResource.getContents().add(genModel);
        return true;
      }
    }
    return false;
  }

  public ResourceSet getGenModelResourceSet()
  {
    Resource resource = getGenModel().eResource();
    if (resource == null && addGenModelToResource(false))
    {
      resource = getGenModel().eResource();
    }

    if (resource != null)
    {
      return resource.getResourceSet();
    }
    else
    {
      return null;
    }
  }

  protected GenModel getOriginalGenModel()
  {
    return originalGenModel;
  }

  public EPackageImportInfo getEPackageImportInfo(EPackage ePackage)
  {
    return (EPackageImportInfo)getEPackageConvertInfo(ePackage);
  }
  
  protected EPackageConvertInfo createEPackageInfo(EPackage ePackage)
  {
    return new EPackageImportInfo();
  }

  protected GenPackage getGenPackage(EPackage ePackage)
  {
    if (getOriginalGenModel() != null && !getOriginalGenModel().getGenPackages().isEmpty())
    {
      for (Iterator i = getOriginalGenModel().getGenPackages().iterator(); i.hasNext();)
      {
        GenPackage referencedGenPackage = (GenPackage)i.next();
        if (referencedGenPackage.getEcorePackage() != null && referencedGenPackage.getEcorePackage().getNsURI().equals(ePackage.getNsURI()))
        {
          return referencedGenPackage;
        }
      }
    }
    return null;
  }

  public void setModelLocation(String location)
  {
    modelLocation = location == null || location.trim().length() == 0 ? null : location;
    modelLocationURIs = null;
  }

  public String getModelLocation()
  {
    return modelLocation;
  }

  public List getModelLocationURIs()
  {
    if (getModelLocation() == null)
    {
      return Collections.EMPTY_LIST;
    }
    else if (modelLocationURIs == null)
    {
      modelLocationURIs = new ArrayList();
      for (StringTokenizer stringTokenizer = new StringTokenizer(getModelLocation()); stringTokenizer.hasMoreTokens();)
      {
        String uri = stringTokenizer.nextToken();
        modelLocationURIs.add(URI.createURI(uri));
      }
    }
    return modelLocationURIs;
  }
  
  public URI getFirstModelLocationURI(boolean resolve)
  {
    List modelLocationURIs = getModelLocationURIs();
    if (!modelLocationURIs.isEmpty())
    {
      URI modelLocationURI = (URI)modelLocationURIs.get(0);
      return resolve ? CommonPlugin.resolve(modelLocationURI) : modelLocationURI;
    }
    return null;
  }

  public void setModelFile(IFile file)
  {
    if (file != null)
    {
      URI modelURI = URI.createPlatformResourceURI(file.getFullPath().toString(), true);
      setModelLocation(modelURI.toString());
    }
    else
    {
      setModelLocation(null);
    }
  }
    
  protected ResourceSet createExternalGenModelResourceSet()
  {
    return getOriginalGenModel() != null ? 
      getOriginalGenModel().eResource().getResourceSet() : 
      super.createExternalGenModelResourceSet();
  }
  
  protected void loadOriginalGenModel(URI genModelURI) throws DiagnosticException
  {
    Resource resource = createResourceSet().getResource(genModelURI, true);
    originalGenModel = (GenModel)resource.getContents().get(0);

    if (getOriginalGenModel() != null)
    {
      getOriginalGenModel().reconcile();

      Diagnostic diagnostic = getOriginalGenModel().diagnose();
      if (diagnostic.getSeverity() != Diagnostic.OK)
      {
        throw new DiagnosticException(diagnostic);
      }

      setGenModelFileName(getOriginalGenModelPath().lastSegment());
      setGenModelContainerPath(getOriginalGenModelPath().removeLastSegments(1));
      genModelPath = getOriginalGenModelPath();
    }

    for (Iterator i = getOriginalGenModel().getUsedGenPackages().iterator(); i.hasNext();)
    {
      GenPackage referencedGenPackage = (GenPackage)i.next();
      getReferencedGenPackages().add(referencedGenPackage);
    }
  }

  public Diagnostic computeEPackages(Monitor monitor) throws Exception
  {
    clearEPackagesCollections();
    Diagnostic diagnostic = doComputeEPackages(monitor);
    presetEPackagesToGenerate();
    return diagnostic;
  }
  
  protected void presetEPackagesToGenerate()
  {
    int size = getEPackages().size(); 
    if (size == 1)
    {
      getEPackageImportInfo((EPackage)getEPackages().get(0)).setConvert(true);
    }
    else if (size > 1)
    {
      List ePackagesBeingReloaded = computeEPackagesBeingReloaded();
      for (Iterator i = ePackagesBeingReloaded.iterator(); i.hasNext();)
      {
        getEPackageImportInfo((EPackage)i.next()).setConvert(true);
      }
    }    
  }

  protected Diagnostic doComputeEPackages(Monitor monitor) throws Exception
  {
    return Diagnostic.OK_INSTANCE;
  }

  public void adjustEPackages(Monitor monitor)
  {
    TreeIterator ePackagesIterator = new AbstractTreeIterator(getEPackages(), false)
    {
      protected Iterator getChildren(Object object)
      {
        return object instanceof Collection ? 
          ((Collection)object).iterator() :
          ((EPackage)object).getESubpackages().iterator();  
      }
    };
    
    while (ePackagesIterator.hasNext())
    {
      EPackage ePackage = (EPackage)ePackagesIterator.next();
      adjustEPackage(monitor, ePackage);
    }
    
    makeEPackageConvertDataUnique();
  }

  protected void adjustEPackage(Monitor monitor, EPackage ePackage)
  {
    EPackageImportInfo ePackageInfo = getEPackageImportInfo(ePackage);

    String name = ePackage.getName();
    int index = name.lastIndexOf(".");
    if (index != -1)
    {
      String basePackage = ePackageInfo.getBasePackage();
      String namePackage = name.substring(0, index);
      name = name.substring(index + 1);
      
      if (basePackage != null && !namePackage.startsWith(basePackage))
      {
        namePackage = basePackage + "." + namePackage;
      }

      StringBuffer basePackageName = new StringBuffer();
      for (StringTokenizer stringTokenizer = new StringTokenizer(namePackage, "."); stringTokenizer.hasMoreTokens(); )
      {
        String packageName = stringTokenizer.nextToken();
        basePackageName.append(CodeGenUtil.safeName(packageName));
        if (stringTokenizer.hasMoreTokens())
        {
          basePackageName.append('.');
        }
      }

      ePackageInfo.setBasePackage(basePackageName.toString());
      ePackage.setName(name);
    }

    if (ePackageInfo.getPrefix() == null)
    {
      ePackageInfo.setPrefix(CodeGenUtil.capName(name));
    }

    if (ePackageInfo.getEcoreFileName() == null)
    {
      String ecoreFileName = null;
      GenPackage genPackage = getGenPackage(ePackage);      
      if (genPackage != null)
      {
        String ePackagePath = genPackage.getEcorePackage().eResource().getURI().lastSegment();
        ecoreFileName = URI.decode(ePackagePath);
      }
      else
      {
        ecoreFileName = ePackage.eResource() == null ? name + ".ecore" : ePackage.eResource().getURI().lastSegment();
      }
      ePackageInfo.setEcoreFileName(ecoreFileName);
    }
  }

  protected IWorkspaceRoot getWorkspaceRoot()
  {
    if (workspaceRoot == null)
    {
      workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
    }
    return workspaceRoot;
  }

  public void prepareGenModelAndEPackages(Monitor monitor)
  {
    ResourceSet resourceSet = getGenModelResourceSet();
    
    resourceSet.getURIConverter().getURIMap().remove(URI.createPlatformResourceURI(getModelProjectName() + "/"));

    // Create resources for all the root EPackages.
    //
    List ePackages = computeEPackagesToConvert();
    for (Iterator i = ePackages.iterator(); i.hasNext();)
    {
      EPackage ePackage = (EPackage)i.next();
      addToResource(ePackage, resourceSet);
    }

    List referencedGenPackages = computeValidReferencedGenPackages(); 
      
    // Create resources for all the referenced EPackages
    // The referencedEPackage is a "local" instance of the realEPackage.  We 
    // will add the former o a resource that has the same URI of the later.
    for (Iterator i = referencedGenPackages.iterator(); i.hasNext();)
    {
      GenPackage genPackage = (GenPackage)i.next();
      EPackage realEPackage = genPackage.getEcorePackage();
      EPackage referredEPackage = getReferredEPackage(genPackage);
      if (referredEPackage != null)
      {
        URI ecoreURI = realEPackage.eResource().getURI();
        Resource resource = resourceSet.createResource(ecoreURI);
        resource.getContents().add(referredEPackage);
      }
    }

    // Initialize the GenModel with all the computed data.
    //
    getGenModel().initialize(ePackages);
    getGenModel().getUsedGenPackages().addAll(referencedGenPackages);
    traverseGenPackages(getGenModel().getGenPackages());
    adjustGenModel(monitor);

    // Restore all configured settings from the original.
    //
    getGenModel().reconcile(getOriginalGenModel());
  }
  
  public void addToResource(EPackage ePackage, ResourceSet resourceSet)
  {
    if (ePackage.eResource() == null)
    {
      EPackageImportInfo ePackageInfo = getEPackageImportInfo(ePackage);
      String fileName = ePackageInfo.getEcoreFileName();
      if (fileName != null)
      {
        String baseLocation = getGenModelPath().removeLastSegments(1).toString() + "/";
        String ecoreFileName = baseLocation + fileName;
        URI ecoreURI = createFileURI(ecoreFileName);
        Resource resource = resourceSet.getResource(ecoreURI, false);
        if (resource == null)
        {
          resource = resourceSet.createResource(ecoreURI);
        }
        resource.getContents().add(ePackage);
      }
    }
  }

  public void saveGenModelAndEPackages(Monitor monitor) throws Exception
  {
    String projectName = getModelProjectName();
    IProject project = getWorkspaceRoot().getProject(projectName);
    if (!project.exists())
    {
      Set referencedGenModels = new HashSet();
      for (Iterator i = getGenModel().getUsedGenPackages().iterator(); i.hasNext();)
      {
        GenPackage genPackage = (GenPackage)i.next();
        referencedGenModels.add(genPackage.getGenModel());
      }
      createProject(monitor, project, referencedGenModels);
    }

    List resources = computeResourcesToBeSaved();    
    String readOnlyFiles = ConverterUtil.WorkspaceResourceValidator.validate(resources);
    if (readOnlyFiles != null)
    {
      throw new Exception(ImporterPlugin.INSTANCE.getString("_UI_ReadOnlyFiles_error", new String[]{readOnlyFiles})); 
    }
    
    for (Iterator i = resources.iterator(); i.hasNext();)
    {
      Resource resource = (Resource)i.next();
      resource.save(getGenmodelSaveOptions());
    }
  }
  
  protected List computeResourcesToBeSaved()
  {
    List resources = new UniqueEList.FastCompare();
    Resource genModelResource = getGenModel().eResource();
    resources.add(genModelResource);
    for (Iterator i = getGenModel().getGenPackages().iterator(); i.hasNext();)
    {
      GenPackage genPackage = (GenPackage)i.next();
      resources.add(genPackage.getEcorePackage().eResource());
    }
    
    // Handle application genmodel stub
    //
    for (Iterator i = getGenModel().getUsedGenPackages().iterator(); i.hasNext();)
    {
      GenPackage genPackage = (GenPackage)i.next();
      if (genPackage.eResource() == genModelResource)
      {
        resources.add(genPackage.getEcorePackage().eResource());
      }
    }
    
    return resources;
  }
  
  protected void createProject(Monitor monitor, IProject project, Collection referencedGenModels)
  {
    IWorkspaceRoot workspaceRoot = getWorkspaceRoot();

    // Determine which projects will need to be referenced.
    //
    List referencedModelProjects = new ArrayList();
    List referencedEditProjects = new ArrayList();
    for (Iterator i = referencedGenModels.iterator(); i.hasNext();)
    {
      GenModel referencedGenModel = (GenModel)i.next();
      String modelDirectory = referencedGenModel.getModelDirectory();
      if (modelDirectory != null)
      {
        referencedModelProjects.add(workspaceRoot.getProject(new Path(modelDirectory).segment(0)));
        String editDirectory = referencedGenModel.getEditDirectory();
        if (editDirectory != null && !modelDirectory.equals(editDirectory))
        {
          referencedEditProjects.add(workspaceRoot.getProject(new Path(editDirectory).segment(0)));
        }
      }
    }

    String projectName = project.getName();
    String path = getGenModel().getModelDirectory();
    int index = path.indexOf(projectName);
    if (index >= 0)
    {
      path = path.substring(index);
    }
    char firstChar = path.charAt(0);
    if (firstChar != '/' && firstChar != '\\')
    {
      path = "/" + path;
    }

    // Create the model project.
    //
    List referencedProjects = new ArrayList(referencedModelProjects);
    Generator.createEMFProject
      (new Path(path),
       getGenModelProjectLocation(),
       referencedProjects,
       monitor,
       Generator.EMF_MODEL_PROJECT_STYLE | Generator.EMF_EMPTY_PROJECT_STYLE);
  }

  protected void adjustGenModel(Monitor monitor)
  {
    String modelName = URI.decode(getGenModelPath().removeFileExtension().lastSegment());
    int index = modelName.lastIndexOf('.');
    if (index != -1)
    {
      modelName = modelName.substring(0, index);
    }
    modelName = CodeGenUtil.capName(modelName); 

    GenModel genModel = getGenModel();
    genModel.setModelName(modelName);
    genModel.setModelPluginID(getModelPluginID());
    genModel.setModelDirectory(getModelPluginDirectory());
    genModel.getUsedGenPackages().addAll(genModel.computeMissingUsedGenPackages());
  }

  protected boolean canConvert(EPackage ePackage)
  {
    return super.canConvert(ePackage) && getEPackageImportInfo(ePackage).getEcoreFileName() != null;
  }

  public void traverseGenPackages(List genPackages)
  {
    for (Iterator i = genPackages.iterator(); i.hasNext();)
    {
      GenPackage genPackage = (GenPackage)i.next();
      EPackage ePackage = genPackage.getEcorePackage();
      EPackageImportInfo ePackageInfo = getEPackageImportInfo(ePackage);

      genPackage.setBasePackage(ePackageInfo.getBasePackage());
      genPackage.setPrefix(ePackageInfo.getPrefix());

      adjustGenPackageDuringTraverse(genPackage);
      traverseGenPackages(genPackage.getNestedGenPackages());
    }
  }

  protected void adjustGenPackageDuringTraverse(GenPackage genPackage)
  {

  }

  protected URI makeRelative(URI uri, URI relativeTo)
  {
    if ("file".equals(uri.scheme()))
    {
      IFile file = getWorkspaceRoot().getFileForLocation(new Path(uri.toFileString()));
      if (file != null)
      {
        URI platformURI = URI.createPlatformResourceURI(file.getFullPath().toString(), true);
        URI result = platformURI.deresolve(relativeTo, false, true, false);
        if (result.isRelative())
        {
          return result;
        }
      }
    }

    URI result = uri.deresolve(relativeTo, true, true, false);
    if (result.isRelative())
    {
      return result;
    }

    return uri;
  }

  protected URI makeAbsolute(URI uri, URI relativeTo)
  {
    if (uri.isRelative())
    {
      return uri.resolve(relativeTo);
    }
    return uri;
  }

  public URI createFileURI(String pathName)
  {
    return usePlatformURI() ? URI.createPlatformResourceURI(pathName, true) : URI.createFileURI(pathName);
  }

  public String getModelPluginID()
  {
    return modelPluginID == null ? ImporterUtil.validPluginID(getModelProjectName()) : modelPluginID;
  }

  public void setModelPluginID(String modelPluginID)
  {
    this.modelPluginID = modelPluginID;
  }

  public String getModelPluginDirectory()
  {
    if (modelPluginDirectory == null)
    {
      String result = getModelProjectName();
      if (result != null)
      {
        if (result.charAt(0) != '/')
        {
          result = "/" + result;
        }
        return result + "/src";
      }
    }
    return modelPluginDirectory;
  }

  public void setModelPluginDirectory(String modelPluginDirectory)
  {
    this.modelPluginDirectory = modelPluginDirectory;
  }

  public String getModelProjectName()
  {
    IPath path = getGenModelProjectLocation();
    if (path != null)
    {
      return URI.decode(path.lastSegment().toString());
    }

    IPath genModelPath = getGenModelPath();
    if (genModelPath != null)
    {
      return URI.decode(genModelPath.segment(0).toString());
    }

    return null;
  }  
      
  protected Map getEcoreSaveOptions()
  {
    return Collections.EMPTY_MAP;
  }
}
