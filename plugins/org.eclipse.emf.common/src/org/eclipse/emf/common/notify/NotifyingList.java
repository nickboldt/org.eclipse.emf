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
 * $Id: NotifyingList.java,v 1.2 2005/06/08 06:19:08 nickb Exp $
 */
package org.eclipse.emf.common.notify;


import org.eclipse.emf.common.util.EList;


/**
 * A managed list that dispatches feature change notification to a notifier.
 */
public interface NotifyingList extends EList
{
  /**
   * Returns the notifier that manages this list.
   * @return the notifier of the list.
   */
  public Object getNotifier();

  /**
   * Returns the notifier's feature that this list represents.
   * @see Notification#getFeature
   * @return the feature of the list.
   */
  public Object getFeature();

  /**
   * Returns the notifier's feature ID that this list represents.
   * @see Notification#getFeatureID
   * @return the feature ID of the list.
   */
  public int getFeatureID();
}
