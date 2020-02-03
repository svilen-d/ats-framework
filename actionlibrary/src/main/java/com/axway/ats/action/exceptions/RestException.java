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
package com.axway.ats.action.exceptions;

import com.axway.ats.common.PublicAtsApi;

/**
 * Exception while working with the REST client
 */
@PublicAtsApi
public class RestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @PublicAtsApi
    public RestException() {

    }

    @PublicAtsApi
    public RestException( String arg0 ) {

        super(arg0);
    }

    @PublicAtsApi
    public RestException( Throwable arg0 ) {

        super(arg0);
    }

    @PublicAtsApi
    public RestException( String arg0,
                          Throwable arg1 ) {

        super(arg0, arg1);
    }
}