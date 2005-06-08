/**
 * <copyright>
 *
 * Copyright (c) 2002-2004 IBM Corporation and others.
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
 * $Id: ResourceImpl.java,v 1.9 2005/06/08 06:20:10 nickb Exp $
 */
package org.eclipse.emf.ecore.resource.impl;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationChain;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.common.notify.impl.NotificationChainImpl;
import org.eclipse.emf.common.notify.impl.NotificationImpl;
import org.eclipse.emf.common.notify.impl.NotifierImpl;
import org.eclipse.emf.common.notify.impl.NotifyingListImpl;
import org.eclipse.emf.common.util.AbstractTreeIterator;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.InternalEList;


/**
 * A highly extensible resource implementation.
 * <p>
 * The following configuration and control mechanisms are provided:
 * <ul>
 *   <li><b>Serialization</b></li>
 *   <ul>
 *     <li>{@link #doSave(OutputStream, Map)}</li>
 *     <li>{@link #doLoad(InputStream, Map)}</li>
 *     <li>{@link #doUnload}</li>
 *   </ul>
 *   <li><b>Root URI Fragment</b></li>
 *   <ul>
 *     <li>{@link #getURIFragmentRootSegment(EObject)}</li>
 *     <li>{@link #getEObjectForURIFragmentRootSegment(String)}</li>
 *   </ul>
 *   <li><b>Containment Changes</b></li>
 *   <ul>
 *     <li>{@link #attached(EObject)}</li>
 *     <li>{@link #detached(EObject)}</li>
 *     <li>{@link #unloaded(InternalEObject)}</li>
 *   </ul>
 *   <li><b>ZIP</b></li>
 *   <ul>
 *     <li>{@link #useZip}</li>
 *     <li>{@link #newContentZipEntry}</li>
 *     <li>{@link #isContentZipEntry(ZipEntry)}</li>
 *   </ul>
 *   <li><b>URI Conversion</b></li>
 *   <ul>
 *     <li>{@link #getURIConverter}</li>
 *   </ul>
 *   <li><b>Modification</b></li>
 *   <ul>
 *     <li>{@link #createModificationTrackingAdapter()}</li>
 *   </ul>
 * </ul>
 * </p>
 */
public class ResourceImpl extends NotifierImpl implements Resource, Resource.Internal
{
  /**
   * The default URI converter when there is no resource set.
   */
  private static URIConverter defaultURIConverter;

  /**
   * Returns the default URI converter that's used when there is no resource set.
   * @return the default URI converter.
   * @see #getURIConverter
   */
  protected static URIConverter getDefaultURIConverter()
  {
    if (defaultURIConverter == null)
    {
      defaultURIConverter = new URIConverterImpl();
    }
    return defaultURIConverter;
  }

  /**
   * The storage for the default save options.
   */
  protected Map defaultSaveOptions;

  /**
   * The storage for the default load options.
   */
  protected Map defaultLoadOptions;

  /**
   * The containing resource set.
   * @see #getResourceSet
   */
  protected ResourceSet resourceSet;

  /**
   * The URI.
   * @see #getURI
   */
  protected URI uri;

  /**
   * The contents.
   * @see #getContents
   */
  protected ContentsEList contents;

  /**
   * The errors.
   * @see #getErrors
   */
  protected EList errors;

  /**
   * The warnings.
   * @see #getErrors
   */
  protected EList warnings;

  /**
   * The modified flag.
   * @see #isModified
   */
  protected boolean isModified;

  /**
   * The loaded flag.
   * @see #isLoaded
   */
  protected boolean isLoaded;

  /**
   * The modification tracking adapter.
   * @see #isTrackingModification
   * @see #attached(EObject)
   * @see #detached(EObject)
   */
  protected Adapter modificationTrackingAdapter;

  /**
   * A map to retrieve the EObject based on the value of its ID feature.
   * @see #setIntrinsicIDToEObjectMap(Map)
   */
  protected Map intrinsicIDToEObjectMap;

  /**
   * Creates a empty instance.
   */
  public ResourceImpl()
  {
  }

  /**
   * Creates an instance with the given URI.
   * @param uri the URI.
   */
  public ResourceImpl(URI uri)
  {
    this();
    this.uri = uri;
  }

  /*
   * Javadoc copied from interface.
   */
  public ResourceSet getResourceSet()
  {
    return resourceSet;
  }

  /**
   * Sets the new containing resource set, and removes the resource from a previous containing resource set, if necessary.
   * @param resourceSet the new containing resource set.
   * @param notifications the accumulating notifications.
   * @return notification of the change.
   */
  public NotificationChain basicSetResourceSet(ResourceSet resourceSet, NotificationChain notifications)
  {
    ResourceSet oldResourceSet = this.resourceSet;
    if (oldResourceSet != null)
    {
      notifications = ((InternalEList)oldResourceSet.getResources()).basicRemove(this, notifications);
    }

    this.resourceSet = resourceSet;

    if (eNotificationRequired())
    {
      if (notifications == null)
      {
        notifications = new NotificationChainImpl(2);
      }
      notifications.add
        (new NotificationImpl(Notification.SET, oldResourceSet, resourceSet)
         {
           public Object getNotifier()
           {
             return ResourceImpl.this;
           }
           public int getFeatureID(Class expectedClass)
           {
             return RESOURCE__RESOURCE_SET;
           }
         });
    }

    return notifications;
  }

  /*
   * Javadoc copied from interface.
   */
  public URI getURI()
  {
    return uri;
  }

  /*
   * Javadoc copied from interface.
   */
  public void setURI(URI uri)
  {
    URI oldURI = this.uri;
    this.uri = uri;
    if (eNotificationRequired())
    {
      Notification notification =
        new NotificationImpl(Notification.SET, oldURI, uri)
        {
          public Object getNotifier()
          {
            return ResourceImpl.this;
          }
          public int getFeatureID(Class expectedClass)
          {
            return RESOURCE__URI;
          }
        };
      eNotify(notification);
    }
  }

  /**
   * A notifying list implementation for supporting {@link Resource#getContents}.
   */
  protected class ContentsEList extends NotifyingListImpl implements InternalEList
  {
    public Object getNotifier()
    {
      return ResourceImpl.this;
    }

    public int getFeatureID()
    {
      return RESOURCE__CONTENTS;
    }

    protected boolean isNotificationRequired()
    {
      return ResourceImpl.this.eNotificationRequired();
    }

    protected boolean useEquals()
    {
      return false;
    }

    protected boolean hasInverse()
    {
      return true;
    }

    protected boolean isUnique()
    {
      return true;
    }

    public NotificationChain inverseAdd(Object object, NotificationChain notifications)
    {
      InternalEObject eObject = (InternalEObject)object;
      notifications = eObject.eSetResource(ResourceImpl.this, notifications);
      ResourceImpl.this.attached(eObject);
      return notifications;
    }

    public NotificationChain inverseRemove(Object object, NotificationChain notifications)
    {
      InternalEObject eObject = (InternalEObject)object;
      if (ResourceImpl.this.isLoaded)
      {
        ResourceImpl.this.detached(eObject);
      }
      return eObject.eSetResource(null, notifications);
    }

    public Iterator basicIterator()
    {
      return super.basicIterator();
    }

    public ListIterator basicListIterator()
    {
      return super.basicListIterator();
    }
  
    public ListIterator basicListIterator(int index)
    {
      return super.basicListIterator(index);
    }

    public List basicList()
    {
      return super.basicList();
    }

    protected Object [] newData(int capacity)
    {
      return new EObject [capacity];
    }

    protected void didAdd(int index, Object object)
    {
      super.didAdd(index, object);
      loaded();
      modified();
    }

    protected void didRemove(int index, Object object)
    {
      super.didRemove(index, object);
      modified();
    }

    protected void didSet(int index, Object newObject, Object oldObject)
    {
      super.didSet(index, newObject, oldObject);
      modified();
    }

    protected void didClear(int oldSize, Object [] oldData)
    {
      if (oldSize == 0)
      {
        loaded();
      }
      else
      {
        super.didClear(oldSize, oldData);
      }
    }

    protected void loaded()
    {
      if (!ResourceImpl.this.isLoaded())
      {
        Notification notification = ResourceImpl.this.setLoaded(true);
        if (notification != null)
        {
          ResourceImpl.this.eNotify(notification);
        }
      }
    }

    protected void modified()
    {
      if (isTrackingModification())
      {
        setModified(true);
      }
    }
  }

  /*
   * Javadoc copied from interface.
   */
  public EList getContents()
  {
    if (contents == null)
    {
      contents = new ContentsEList();
    }
    return contents;
  }

  /*
   * Javadoc copied from interface.
   */
  public TreeIterator getAllContents()
  {
    return
      new AbstractTreeIterator(this, false)
      {
        public Iterator getChildren(Object object)
        {
          return object == ResourceImpl.this ? ResourceImpl.this.getContents().iterator() : ((EObject)object).eContents().iterator();
        }
      };
  }

  /*
   * Javadoc copied from interface.
   */
  public EList getErrors()
  {
    if (errors == null)
    {
      errors =
        new NotifyingListImpl()
        {
          protected boolean isNotificationRequired()
          {
             return ResourceImpl.this.eNotificationRequired();
          }

          public Object getNotifier()
          {
            return ResourceImpl.this;
          }

          public int getFeatureID()
          {
            return RESOURCE__ERRORS;
          }
        };
    }
    return errors;
  }

  /*
   * Javadoc copied from interface.
   */
  public EList getWarnings()
  {
    if (warnings == null)
    {
      warnings =
        new NotifyingListImpl()
        {
          protected boolean isNotificationRequired()
          {
             return ResourceImpl.this.eNotificationRequired();
          }

          public Object getNotifier()
          {
            return ResourceImpl.this;
          }

          public int getFeatureID()
          {
            return RESOURCE__WARNINGS;
          }
        };
    }
    return warnings;
  }

  /**
   * Returns whether contents will be compressed.
   * This implementation returns <code>false</code>.
   * When this returns <code>true</code>,
   * {@link #save(OutputStream, Map)} and {@link #load(InputStream, Map)}
   * will zip compress and decompress contents.
   * @return whether contents will be compressed.
   * @see #newContentZipEntry
   * @see #isContentZipEntry(ZipEntry)
   */
  protected boolean useZip()
  {
    return false;
  }


  /**
   * Returns the URI fragment root segment for reaching the given direct content object.
   * This default implementation returns the position of the object, if there is more than one object,
   * otherwise, the empty string.
   * As a result, the URI fragment for a single root object will be <code>"/"</code>.
   * @return the URI fragment root segment for reaching the given direct content object.
   */
  protected String getURIFragmentRootSegment(EObject eObject)
  {
    List contents = getContents();
    return contents.size() > 1 ?
      Integer.toString(getContents().indexOf(eObject)) :
      "";
  }

  /*
   * Javadoc copied from interface.
   */
  public String getURIFragment(EObject eObject)
  {
    String id = EcoreUtil.getID(eObject);
    if (id != null)
    {
      return id;
    }
    else
    {
      List uriFragmentPath = new ArrayList();
      for (EObject container = eObject.eContainer(); container != null; container = eObject.eContainer())
      {
        uriFragmentPath.add(((InternalEObject)container).eURIFragmentSegment(eObject.eContainingFeature(), eObject));
        eObject = container;
      }

      StringBuffer result = new StringBuffer("/");
      result.append(getURIFragmentRootSegment(eObject));

      for (ListIterator i = uriFragmentPath.listIterator(uriFragmentPath.size()); i.hasPrevious(); )
      {
        result.append('/');
        result.append((String)i.previous());
      }
      return result.toString();
    }
  }

  /**
   * Returns the object associated with the URI fragment root segment.
   * This default implementation uses the position of the object;
   * an empty string is the same as <code>"0"</code>.
   * @return the object associated with the URI fragment root segment.
   */
  protected EObject getEObjectForURIFragmentRootSegment(String uriFragmentRootSegment)
  {
    int position =  0;
    if (uriFragmentRootSegment.length() > 0)
    {
      try
      {
        position = Integer.parseInt(uriFragmentRootSegment);
      }
      catch (NumberFormatException exception)
      {
        throw new WrappedException(exception);
      }
    }

    List contents = getContents();
    if (position < contents.size())
    {
      return (EObject)contents.get(position);
    }
    else
    {
      return null;
    }
  }

  /*
   * Javadoc copied from interface.
   */
  public EObject getEObject(String uriFragment)
  {
    int length = uriFragment.length();
    if (length > 0)
    {
      if (uriFragment.charAt(0) == '/')
      {
        ArrayList uriFragmentPath = new ArrayList(4);
        int start = 1;
        for (int i = 1; i < length; ++i)
        {
          if (uriFragment.charAt(i) == '/')
          {
            uriFragmentPath.add(start == i ? "" : uriFragment.substring(start, i));
            start = i + 1;
          }
        }
        uriFragmentPath.add(uriFragment.substring(start));
        return getEObject(uriFragmentPath);
      }
      else if (uriFragment.charAt(length - 1) == '?')
      {
        int index = uriFragment.lastIndexOf('?', length - 2);
        if (index > 0)
        {
          uriFragment = uriFragment.substring(0, index);
        }
      }
    }
    
    return getEObjectByID(uriFragment);
  }

  /**
   * Returns the object based on the fragment path as a list of Strings.
   */
  protected EObject getEObject(List uriFragmentPath)
  {
    int size = uriFragmentPath.size();
    EObject eObject = getEObjectForURIFragmentRootSegment(size == 0 ? "" : (String)uriFragmentPath.get(0));
    for (int i = 1; i < size && eObject != null; ++i)
    {
      eObject = ((InternalEObject)eObject).eObjectForURIFragmentSegment((String)uriFragmentPath.get(i));
    }

    return eObject;
  }

  /**
   * Returns the map used to cache the EObject that is identified by the {@link #getEObjectByID(String) value} 
   * of its ID feature.
   * @return the map used to cache the EObject that is identified by the value of its ID feature.
   * @see #setIntrinsicIDToEObjectMap
   */
  public Map getIntrinsicIDToEObjectMap()
  {
    return intrinsicIDToEObjectMap;
  }

  /**
   * Sets the map used to cache the EObject identified by the value of its ID feature.
   * This cache is only activated if the map is not <code>null</code>.  
   * The map will be lazily loaded by the {@link #getEObjectByID(String) getEObjectByID} method.
   * It is up to the client to clear the cache when it becomes invalid, 
   * e.g., when the ID of a previously mapped EObject is changed.
   * @param intrinsicIDToEObjectMap the new map or <code>null</code>.
   * @see #getIntrinsicIDToEObjectMap
   */
  public void setIntrinsicIDToEObjectMap(Map intrinsicIDToEObjectMap)
  {
    this.intrinsicIDToEObjectMap = intrinsicIDToEObjectMap;
  }
  

  /**
   * Returns the object based on the fragment as an ID.
   */
  protected EObject getEObjectByID(String id)
  {
    Map map = getIntrinsicIDToEObjectMap();
    if (map != null)
    {
      EObject eObject = (EObject)map.get(id);
      if (eObject != null)
      {
        return eObject;
      }
    }
    
    for (Iterator i = getAllContents(); i.hasNext(); )
    {
      EObject eObject = (EObject)i.next();
      String eObjectId = EcoreUtil.getID(eObject);
      if (eObjectId != null)
      {
        if (map != null)
        {
          map.put(eObjectId, eObject);
        }
        
        if (eObjectId.equals(id))
        {
          return eObject;
        }
      }
    }

    return null;
  }

  public void attached(EObject eObject)
  {   
    if (isAttachedDetachedHelperRequired())
    {
      attachedHelper(eObject);
      for (Iterator tree = eObject.eAllContents(); tree.hasNext(); )
      {
        attachedHelper((EObject)tree.next());
      }
    }
  }
  
  protected boolean isAttachedDetachedHelperRequired()
  {
    return isTrackingModification() || getIntrinsicIDToEObjectMap() != null;
  }

  protected void attachedHelper(EObject eObject)
  {
    if (isTrackingModification())
    {
      eObject.eAdapters().add(modificationTrackingAdapter);
    }
    
    Map map = getIntrinsicIDToEObjectMap();
    if (map != null)
    {
      String id = EcoreUtil.getID(eObject);
      if (id != null)
      {
        map.put(id, eObject);
      }
    }    
  }
  

  /**
   * Adds modification tracking adapters to the object and it's content tree.
   * @param eObject the object.
   * @see #attached(EObject)
   * @deprecated since 2.1.0.  This method is not invoked anymore.  See 
   * {@link #attachedHelper(EObject)}.
   */
  final protected void addModificationTrackingAdapters(EObject eObject)
  {
  }

  public void detached(EObject eObject)
  {
    if (isAttachedDetachedHelperRequired())
    {
      detachedHelper(eObject);
      for (Iterator tree = eObject.eAllContents(); tree.hasNext(); )
      {
        detachedHelper((EObject)tree.next());
      }
    }
  }
  
  protected void detachedHelper(EObject eObject)
  {
    Map map = getIntrinsicIDToEObjectMap();
    if (map != null)
    {
      String id = EcoreUtil.getID(eObject);
      if (id != null)
      {
        map.remove(id);
      }
    }

    if (isTrackingModification())
    {
      eObject.eAdapters().remove(modificationTrackingAdapter);
    }
  }  

  /**
   * Removes modification tracking adapters to the object and it's content tree.
   * @param eObject the object.
   * @see #detached(EObject)
   * @deprecated since 2.1.0.  This method is not invoked anymore.  See 
   * {@link #attachedHelper(EObject)}.
   */
  final protected void removeModificationTrackingAdapters(EObject eObject)
  {
  }


  /**
   * Returns the URI converter.
   * This typically gets the {@link ResourceSet#getURIConverter converter}
   * from the {@link #getResourceSet containing} resource set,
   * but it calls {@link #getDefaultURIConverter} when there is no containing resource set.
   * @return the URI converter.
   */
  protected URIConverter getURIConverter()
  {
    return
      getResourceSet() == null ?
        getDefaultURIConverter() :
        getResourceSet().getURIConverter();
  }

  /*
   * Javadoc copied from interface.
   */
  public void save(Map options) throws IOException
  {
    URIConverter uriConverter = getURIConverter();
    OutputStream outputStream = uriConverter.createOutputStream(getURI());
    try
    {
      save(outputStream, options);
    }
    finally
    {
      outputStream.close();
    }
  }

  /*
   * Javadoc copied from interface.
   */
  public void load(Map options) throws IOException
  {
    if (!isLoaded)
    {
      URIConverter uriConverter = getURIConverter();
      InputStream inputStream = uriConverter.createInputStream(getURI());
      try
      {
        load(inputStream, options);
      }
      finally
      {
        inputStream.close();
      }
    }
  }

  /**
   * Returns a new zip entry for {@link #save(OutputStream, Map) saving} the resource contents.
   * It is called by {@link #save(OutputStream, Map)} when writing {@link #useZip zipped} contents.
   * This implementation creates an entry called <code>ResourceContents</code>.
   * @return a new zip entry.
   * @see #isContentZipEntry(ZipEntry)
   */
  protected ZipEntry newContentZipEntry()
  {
    return new ZipEntry("ResourceContents");
  }

  /**
   * Saves the resource to the output stream using the specified options.
   * <p>
   * This implementation is <code>final</code>;
   * clients should override {@link #doSave doSave}.
   * </p>
   * @param options the save options.
   * @see #save(Map)
   * @see #doSave(OutputStream, Map)
   * @see #load(InputStream, Map)
   */
  public final void save(OutputStream outputStream, Map options) throws IOException
  {
    ZipOutputStream zipOutputStream = null;
    if (useZip())
    {
      zipOutputStream = 
        new ZipOutputStream(outputStream)
        {
          public void flush()
          {
          }
          public void close() throws IOException
          {
            try
            {
              super.flush();
            }
            catch (IOException exception)
            {
            }
            super.close();
          }
        };
      zipOutputStream.putNextEntry(newContentZipEntry());
      outputStream = zipOutputStream;
    }

    if (defaultSaveOptions == null || defaultSaveOptions.isEmpty())
    {
      doSave(outputStream, options);
    }
    else if (options == null)
    {
      doSave(outputStream, defaultSaveOptions);
    }
    else
    {
      Map mergedOptions = new HashMap(defaultSaveOptions);
      mergedOptions.putAll(options);

      doSave(outputStream, mergedOptions);
    }

    setModified(false);
    if (zipOutputStream != null)
    {
      zipOutputStream.finish();
    }
  }

  /**
   * Called to save the resource.
   * This implementation throws an exception;
   * clients must override it.
   * @param outputStream the stream
   * @param options the save options.
   * @exception UnsupportedOperationException.
   */
  protected void doSave(OutputStream outputStream, Map options) throws IOException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns whether the given entry is the content entry for this resource.
   * It is called by {@link #load(InputStream, Map)} when reading {@link #useZip zipped} contents.
   * This implementation return <code>true</code>;
   * i.e., the first entry will be read.
   * @return whether the given entry is the content entry for this resource.
   * @see #newContentZipEntry
   */
  protected boolean isContentZipEntry(ZipEntry zipEntry)
  {
    return true;
  }

  /*
   * Javadoc copied from interface.
   */
  public final void load(InputStream inputStream, Map options) throws IOException
  {
    if (!isLoaded)
    {
      Notification notification = setLoaded(true);

      if (errors != null)
      {
        errors.clear();
      }

      if (warnings != null)
      {
        warnings.clear();
      }

      if (useZip())
      {
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        while (zipInputStream.available() != 0)
        {
          ZipEntry zipEntry = zipInputStream.getNextEntry();
          if (isContentZipEntry(zipEntry))
          {
            inputStream = zipInputStream;
            break;
          }
        }
      }

      try
      {
        if (defaultLoadOptions == null || defaultLoadOptions.isEmpty())
        {
          doLoad(inputStream, options);
        }
        else if (options == null)
        {
          doLoad(inputStream, defaultLoadOptions);
        }
        else
        {
          Map mergedOptions = new HashMap(defaultLoadOptions);
          mergedOptions.putAll(options);
  
          doLoad(inputStream, mergedOptions);
        }
      }
      finally
      {
        if (notification != null)
        {
          eNotify(notification);
        }
  
        setModified(false);
      } 
    }
  }

  /**
   * Called to load the resource.
   * This implementation throws an exception;
   * clients must override it.
   * @param inputStream the stream
   * @param options the load options.
   * @exception UnsupportedOperationException.
   */
  protected void doLoad(InputStream inputStream, Map options) throws IOException
  {
    throw new UnsupportedOperationException();
  }

  /*
   * Javadoc copied from interface.
   */
  public boolean isLoaded()
  {
    return isLoaded;
  }

  /**
   * Called when the object is unloaded.
   * This implementation 
   * {@link InternalEObject#eSetProxyURI sets} the object to be a proxy 
   * and clears the {@link #eAdapters adapters}.
   */
  protected void unloaded(InternalEObject internalEObject)
  {
    internalEObject.eSetProxyURI(uri.appendFragment(getURIFragment(internalEObject)));
    internalEObject.eAdapters().clear();
  }

  /**
   * Sets the load state as indicated, and returns a notification, if {@link org.eclipse.emf.common.notify.impl.BasicNotifierImpl#eNotificationRequired required}.
   * Clients are <b>not</b> expected to call this directly; it is managed by the implementation.
   * @param isLoaded whether the resource is loaded.
   * @return a notification.
   */
  protected Notification setLoaded(boolean isLoaded)
  {
    boolean oldIsLoaded = this.isLoaded;
    this.isLoaded = isLoaded;

    if (eNotificationRequired())
    {
      Notification notification =
        new NotificationImpl(Notification.SET, oldIsLoaded, isLoaded)
        {
          public Object getNotifier()
          {
            return ResourceImpl.this;
          }
          public int getFeatureID(Class expectedClass)
          {
            return RESOURCE__IS_LOADED;
          }
        };
      return notification;
    }
    else
    {
      return null;
    }
  }

  /**
   * Does all the work of unloading the resource.
   * It calls {@link #unloaded unloaded} for each object it the content {@link #getAllContents tree},
   * and clears the {@link #getContents contents}, {@link #getErrors errors}, and {@link #getWarnings warnings}.
   */
  protected void doUnload()
  {
    Iterator allContents = EcoreUtil.getAllContents(new ArrayList(getContents()));

    // This guard is needed to ensure that clear doesn't make the resource become loaded.
    //
    if (!getContents().isEmpty())
    {
      getContents().clear();
    }
    getErrors().clear();
    getWarnings().clear();

    while (allContents.hasNext())
    {
      unloaded((InternalEObject)allContents.next());
    }
  }

  /*
   * Javadoc copied from interface.
   */
  public final void unload()
  {
    if (isLoaded)
    {
      Notification notification = setLoaded(false);
      doUnload();
      if (notification != null)
      {
        eNotify(notification);
      }
    }
  }

  /**
   * An adapter implementation for tracking resource modification.
   */
  protected class ModificationTrackingAdapter extends AdapterImpl
  {
    public void notifyChanged(Notification notification)
    {
      switch (notification.getEventType())
      {
        case Notification.SET:
        case Notification.UNSET:
        case Notification.MOVE:
        {
          if (!notification.isTouch())
          {
            setModified(true);
          }
          break;
        }
        case Notification.ADD:
        case Notification.REMOVE:
        case Notification.ADD_MANY:
        case Notification.REMOVE_MANY:
        {
          setModified(true);
          break;
        }
      }
    }
  }

  /*
   * Javadoc copied from interface.
   */
  public boolean isTrackingModification()
  {
    return modificationTrackingAdapter != null;
  }

  /*
   * Javadoc copied from interface.
   */
  public void setTrackingModification(boolean isTrackingModification)
  {
    boolean oldIsTrackingModification = modificationTrackingAdapter != null;

    if (oldIsTrackingModification != isTrackingModification)
    {
      if (isTrackingModification)
      {
        modificationTrackingAdapter = createModificationTrackingAdapter();
        
        for (Iterator i = getAllContents(); i.hasNext(); )
        {
          EObject eObject = (EObject)i.next();
          eObject.eAdapters().add(modificationTrackingAdapter);
        }
      }
      else
      {
        Adapter oldModificationTrackingAdapter = modificationTrackingAdapter;
        modificationTrackingAdapter = null;
        
        for (Iterator i = getAllContents(); i.hasNext(); )
        {
          EObject eObject = (EObject)i.next();
          eObject.eAdapters().remove(oldModificationTrackingAdapter);
        }
      }
    }

    if (eNotificationRequired())
    {
      Notification notification =
        new NotificationImpl(Notification.SET, oldIsTrackingModification, isTrackingModification)
        {
          public Object getNotifier()
          {
            return ResourceImpl.this;
          }
          public int getFeatureID(Class expectedClass)
          {
            return RESOURCE__IS_TRACKING_MODIFICATION;
          }
        };
      eNotify(notification);
    }
  }


  /**
   * Creates a modification tracking adapter.
   * This implementation creates a {@link ResourceImpl.ModificationTrackingAdapter}.
   * Clients may override this to any adapter.
   * @see #modificationTrackingAdapter
   * @see #isTrackingModification
   */
  protected Adapter createModificationTrackingAdapter()
  {
    return new ModificationTrackingAdapter();
  }

  /*
   * Javadoc copied from interface.
   */
  public boolean isModified()
  {
    return isModified;
  }

  /*
   * Javadoc copied from interface.
   */
  public void setModified(boolean isModified)
  {
    boolean oldIsModified = this.isModified;
    this.isModified = isModified;
    if (eNotificationRequired())
    {
      Notification notification =
        new NotificationImpl(Notification.SET, oldIsModified, isModified)
        {
          public Object getNotifier()
          {
            return ResourceImpl.this;
          }
          public int getFeatureID(Class expectedClass)
          {
            return RESOURCE__IS_MODIFIED;
          }
        };
      eNotify(notification);
    }
  }

  /**
   * If an implementation uses IDs and stores the IDs as part of the resource
   * rather than as objects, this method should return a string representation of
   * the ID to object mapping, which might be implemented as a Java Map.
   * @return a string representation of the ID to object mapping
   */
  public String toKeyString()
  {
    StringBuffer result = new StringBuffer("Key type: ");
    result.append(getClass().toString());
    return result.toString();
  }

  public String toString()
  {
    return
      getClass().getName() + '@' + Integer.toHexString(hashCode()) +
        " uri='" + uri + "'";
  }
}
