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
package org.wso2.sample.workflow;

import org.apache.axis2.util.URL;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.workflow.GeneralWorkflowResponse;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowConstants;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;

import java.io.IOException;
import java.util.List;

public class MultistepSubsWorkflow extends WorkflowExecutor {

    private String bpsUser;

    private String bpsPass;

    private String bpsURL;

    private String callback;

    private String processDefinitionId;

    private static final Log log = LogFactory.getLog(MultistepSubsWorkflow.class);

    public String getWorkflowType() {
        return WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_CREATION;
    }

    public List<WorkflowDTO> getWorkflowDetails(String s) throws WorkflowException {
        return null;
    }

    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException{

        SubscriptionWorkflowDTO subsWorkflowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        String payload = buildJSONPayloadForBusinessProcess(subsWorkflowDTO);

        URL serviceEndpointURL = new URL(getBpsURL());
        HttpClient httpClient = APIUtil.getHttpClient(serviceEndpointURL.getPort(),
                serviceEndpointURL.getProtocol());

        HttpPost httpPost = new HttpPost(getBpsURL());
        //Generate the basic auth header using provided user credentials
        String authHeader = getBasicAuthHeader();
        httpPost.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        StringEntity requestEntity = new StringEntity(payload, ContentType.APPLICATION_JSON);

        httpPost.setEntity(requestEntity);
        try {
            HttpResponse response = httpClient.execute(httpPost);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                String error = "Error while starting the process:  " + response.getStatusLine().getStatusCode()
                        + " " + response.getStatusLine().getReasonPhrase();
                log.error(error);
                throw new WorkflowException(error);
            }
        } catch (ClientProtocolException e) {
            String errorMsg = "Error while creating the http client";
            log.error(errorMsg, e);
            throw new WorkflowException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "Error while connecting to the BPMN process server from the WorkflowExecutor.";
            log.error(errorMsg, e);
            throw new WorkflowException(errorMsg, e);
        } finally {
            httpPost.reset();
        }

        super.execute(workflowDTO);

        return new GeneralWorkflowResponse();
    }

    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {
        workflowDTO.setUpdatedTime(System.currentTimeMillis());
        super.complete(workflowDTO);
        log.info("Subscription Creation [Complete] Workflow Invoked. Workflow ID : " + workflowDTO
                .getExternalWorkflowReference() + "Workflow State : " + workflowDTO.getStatus());

        if (WorkflowStatus.APPROVED.equals(workflowDTO.getStatus())) {
            ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
            try {
                apiMgtDAO.updateSubscriptionStatus(Integer.parseInt(workflowDTO.getWorkflowReference()),
                        APIConstants.SubscriptionStatus.UNBLOCKED);
            } catch (APIManagementException e) {
                log.error("Could not complete subscription creation workflow", e);
                throw new WorkflowException("Could not complete subscription creation workflow", e);
            }
        } else if (WorkflowStatus.REJECTED.equals(workflowDTO.getStatus())) {
            ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
            try {
                apiMgtDAO.updateSubscriptionStatus(Integer.parseInt(workflowDTO.getWorkflowReference()),
                        APIConstants.SubscriptionStatus.REJECTED);
            } catch (APIManagementException e) {
                log.error("Could not complete subscription creation workflow", e);
                throw new WorkflowException("Could not complete subscription creation workflow", e);
            }
        }
        return new GeneralWorkflowResponse();
    }

    private String buildJSONPayloadForBusinessProcess(SubscriptionWorkflowDTO workflowDTO){

        JSONArray variables = new JSONArray();

        JSONObject externalRef = new JSONObject();
        externalRef.put(WorkflowConstants.PayloadConstants.VARIABLE_NAME, "workflowExternalRef");
        externalRef.put(WorkflowConstants.PayloadConstants.VARIABLE_VALUE, workflowDTO.getExternalWorkflowReference());
        variables.add(externalRef);

        JSONObject callbackURL = new JSONObject();
        callbackURL.put(WorkflowConstants.PayloadConstants.VARIABLE_NAME, "callBackURL");
        callbackURL.put(WorkflowConstants.PayloadConstants.VARIABLE_VALUE, getCallback());
        variables.add(callbackURL);

        JSONObject apiName = new JSONObject();
        apiName.put(WorkflowConstants.PayloadConstants.VARIABLE_NAME, "apiName");
        apiName.put(WorkflowConstants.PayloadConstants.VARIABLE_VALUE, workflowDTO.getApiName());
        variables.add(apiName);

        JSONObject apiVersion = new JSONObject();
        apiVersion.put(WorkflowConstants.PayloadConstants.VARIABLE_NAME, "apiVersion");
        apiVersion.put(WorkflowConstants.PayloadConstants.VARIABLE_VALUE, workflowDTO.getApiVersion());
        variables.add(apiVersion);

        JSONObject subscriber = new JSONObject();
        subscriber.put(WorkflowConstants.PayloadConstants.VARIABLE_NAME, "subscriber");
        subscriber.put(WorkflowConstants.PayloadConstants.VARIABLE_VALUE, workflowDTO.getSubscriber());
        variables.add(subscriber);

        JSONObject applicationName = new JSONObject();
        applicationName.put(WorkflowConstants.PayloadConstants.VARIABLE_NAME, "applicationName");
        applicationName.put(WorkflowConstants.PayloadConstants.VARIABLE_VALUE, workflowDTO.getApplicationName());
        variables.add(applicationName);

        JSONObject tierName = new JSONObject();
        tierName.put(WorkflowConstants.PayloadConstants.VARIABLE_NAME, "tierName");
        tierName.put(WorkflowConstants.PayloadConstants.VARIABLE_VALUE, workflowDTO.getTierName());
        variables.add(tierName);

        JSONObject payload = new JSONObject();
        payload.put("processDefinitionId", getProcessDefinitionId());
        payload.put("businessKey", "");
        payload.put("variables", variables);

        return payload.toJSONString();
    }

    private String getBasicAuthHeader() {
        byte[] encodedAuth = Base64.encodeBase64((getBpsUser() + ":" + getBpsPass()).getBytes());
        return "Basic " + new String(encodedAuth);
    }

    public String getBpsUser() {
        return bpsUser;
    }

    public void setBpsUser(String bpsUser) {
        this.bpsUser = bpsUser;
    }

    public String getBpsPass() {
        return bpsPass;
    }

    public void setBpsPass(String bpsPass) {
        this.bpsPass = bpsPass;
    }

    public String getBpsURL() {
        return bpsURL;
    }

    public void setBpsURL(String bpsURL) {
        this.bpsURL = bpsURL;
    }

    public String getCallback() {
        return callback;
    }

    public void setCallback(String callback) {
        this.callback = callback;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }
}
