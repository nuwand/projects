/*
 *   Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.sample.tokengenerator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.wso2.carbon.identity.oauth2.token.OAuthTokenReqMessageContext;
import org.wso2.carbon.identity.oauth2.token.OauthTokenIssuerImpl;

import java.util.UUID;

/**
 * The custom token generator which prefixes the region identifier to the token
 */
public class CustomTokenGenerator extends OauthTokenIssuerImpl {

    private static final Log log = LogFactory.getLog(CustomTokenGenerator.class);

    private static final String REGION_ID = "__REGION__";

    @Override
    public String accessToken(OAuthTokenReqMessageContext tokReqMsgCtx) throws OAuthSystemException {

        String regionID = System.getProperty(REGION_ID);

        if(log.isDebugEnabled()){
            log.debug("Region ID = " + regionID);
        }

        String accessToken = UUID.randomUUID().toString();

        return regionID != null ? regionID + accessToken : accessToken;
    }
}
