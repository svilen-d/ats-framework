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
package com.axway.ats.uiengine.elements;

import com.axway.ats.common.PublicAtsApi;
import com.axway.ats.uiengine.UiDriver;

/**
 * A Radio Button
 */
@PublicAtsApi
public abstract class UiRadioButton extends UiElement {

    public UiRadioButton( UiDriver uiDriver,
                          UiElementProperties properties ) {

        super( uiDriver, properties );
    }

    /**
     * Select radio button
     *
     */
    @PublicAtsApi
    public abstract void select();

    /**
     *
     * @return is the radio button selected
     */
    @PublicAtsApi
    public abstract boolean isSelected();

}
