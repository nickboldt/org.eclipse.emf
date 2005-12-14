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
 * $Id: ImporterPlugin.java,v 1.5 2005/12/14 07:48:49 marcelop Exp $
 */
package org.eclipse.emf.importer;

import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.ui.EclipseUIPlugin;
import org.eclipse.emf.common.util.ResourceLocator;
import org.eclipse.emf.converter.ConverterPlugin;


/**
 * The <b>Plugin</b> for the model importer.
 * @since 2.1.0
 */
public final class ImporterPlugin extends EMFPlugin
{
  /**
   * The singleton instance of the plugin.
   */
  public static final ImporterPlugin INSTANCE = new ImporterPlugin();

  /**
   * The one instance of this class.
   */
  private static Implementation plugin;
  
  public static final String ID = "org.eclipse.emf.importer";

  /**
   * Creates the singleton instance.
   */
  private ImporterPlugin()
  {
    super
      (new ResourceLocator []
       {
         ConverterPlugin.INSTANCE
       });
  }

  /*
   * Javadoc copied from base class.
   */
  public ResourceLocator getPluginResourceLocator()
  {
    return plugin;
  }

  /**
   * Returns the singleton instance of the Eclipse plugin.
   * @return the singleton instance.
   */
  public static Implementation getPlugin()
  {
    return plugin;
  }

  /**
   * The actual implementation of the Eclipse <b>Plugin</b>.
   */
  public static class Implementation extends EclipseUIPlugin
  {
    /**
     * Creates an instance.
     */
    public Implementation()
    {
      super();

      // Remember the static instance.
      //
      plugin = this;
    }
  }
}
