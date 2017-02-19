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
package org.wso2.sample.token.validator;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpStatus;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.rest.AbstractHandler;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.util.RelayUtils;
import org.wso2.carbon.apimgt.gateway.handlers.Utils;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityConstants;

import java.util.Map;

/**
 * This class checks whether the token has the appropriate prefix.
 */
public class RegionValidator extends AbstractHandler {

    private static final Log log = LogFactory.getLog(RegionValidator.class);

    private static final String REGION_ID = "__REGION__";

    public boolean handleRequest(MessageContext messageContext) {

        String regionId = System.getProperty(REGION_ID);

        if (log.isDebugEnabled()) {
            log.debug("Region ID = " + regionId);
        }

        if(regionId == null || regionId.length() == 0){
            //No region specified, nothing to validate for.
            return true;
        }

        Map headers = (Map) ((Axis2MessageContext) messageContext).getAxis2MessageContext().
                getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);

        if(headers != null){
            String authorizationHeader = String.valueOf(headers.get(HTTPConstants.HEADER_AUTHORIZATION));

            if (authorizationHeader == null) {
                //No header provided, nothing to validate
                return true;
            }

            String bearerToken = null;
            for(String authzToken : authorizationHeader.split(",")){
                if(authzToken.startsWith("Bearer")){
                    bearerToken = authzToken;
                    break;
                }
            }

            if(bearerToken == null || bearerToken.split(" ")[1].startsWith(regionId)){
                //No bearer token provided or the provided bearer token is of the expected region.
                return true;
            }
            handleAuthFailure(messageContext);
            return false;
        }
        return true;
    }

    public boolean handleResponse(MessageContext messageContext) {
        return true;
    }

    private void handleAuthFailure(MessageContext messageContext) {
        messageContext.setProperty(SynapseConstants.ERROR_CODE, 900900);
        messageContext.setProperty(SynapseConstants.ERROR_MESSAGE,
                "Unclassified Authentication Failure");

        Mediator sequence = messageContext.getSequence("_auth_failure_handler_");
        // Invoke the custom error handler specified by the user
        if (sequence != null && !sequence.mediate(messageContext)) {
            // If needed user should be able to prevent the rest of the fault handling
            // logic from getting executed
            return;
        }
        // By default we send a 401 response back
        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext).
                getAxis2MessageContext();
        // This property need to be set to avoid sending the content in pass-through pipe (request message)
        // as the response.
        axis2MC.setProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED, Boolean.TRUE);
        try {
            RelayUtils.consumeAndDiscardMessage(axis2MC);
        } catch (AxisFault axisFault) {
            //In case of an error it is logged and the process is continued because we're setting a fault message in the payload.
            log.error("Error occurred while consuming and discarding the message", axisFault);
        }
        axis2MC.setProperty(Constants.Configuration.MESSAGE_TYPE, "application/soap+xml");

        int status = HttpStatus.SC_FORBIDDEN;

        if (messageContext.isDoingPOX() || messageContext.isDoingGET()) {
            Utils.setFaultPayload(messageContext, getFaultPayload(status));
        } else {
            Utils.setSOAPFault(messageContext, "Client", "Authentication Failure", "Missing Token Prefix");
        }
        Utils.sendFault(messageContext, status);
    }

    private OMElement getFaultPayload(int errorCode) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMNamespace ns = fac.createOMNamespace(APISecurityConstants.API_SECURITY_NS,
                APISecurityConstants.API_SECURITY_NS_PREFIX);
        OMElement payload = fac.createOMElement("fault", ns);

        OMElement errorCodeStr = fac.createOMElement("code", ns);
        errorCodeStr.setText("900900");
        OMElement errorMessage = fac.createOMElement("message", ns);
        errorMessage.setText(APISecurityConstants.getAuthenticationFailureMessage(errorCode));
        OMElement errorDetail = fac.createOMElement("description", ns);
        errorDetail.setText(APISecurityConstants.getFailureMessageDetailDescription(errorCode,
                "Unclassified Authentication Failure"));

        payload.addChild(errorCodeStr);
        payload.addChild(errorMessage);
        payload.addChild(errorDetail);
        return payload;
    }
}
