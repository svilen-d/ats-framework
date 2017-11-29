/*
 * Copyright 2017 Axway Software
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.axway.ats.core.uiengine.swt;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ISwtTree extends Remote {

    public void select(
                        String label,
                        String inGroup,
                        int index,
                        String... treeItems ) throws RemoteException;

    public void select(
                        String label,
                        String inGroup,
                        int index,
                        int... indices ) throws RemoteException;

    public void expand(
                        String label,
                        String inGroup,
                        int index,
                        String... treeItems ) throws RemoteException;

    public void collapse(
                          String label,
                          String inGroup,
                          int index,
                          String treeItem ) throws RemoteException;

    public void click(
                       String label,
                       String inGroup,
                       int index,
                       String... treeItems ) throws RemoteException;

    public void doubleClick(
                             String label,
                             String inGroup,
                             int index,
                             String... treeItems ) throws RemoteException;

    public void contextMenu(
                             String label,
                             String inGroup,
                             int index,
                             String[] treeItems,
                             String... menuLabels ) throws RemoteException;
}
