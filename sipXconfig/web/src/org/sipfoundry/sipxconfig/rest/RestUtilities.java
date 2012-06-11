/*
 *
 *  Copyright (C) 2012 PATLive, D. Chang
 *  Contributed to SIPfoundry under a Contributor Agreement
 *  OpenAcdUtilities.java - Support functionality for OpenAcd Restlets
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.

 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.sipfoundry.sipxconfig.rest;

import java.io.IOException;
import java.util.List;

import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.sipfoundry.sipxconfig.branch.Branch;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgent;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdClient;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueue;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueueGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeAction;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeCondition;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeStep;
import org.sipfoundry.sipxconfig.openacd.OpenAcdReleaseCode;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkillGroup;
import org.sipfoundry.sipxconfig.permission.Permission;
import org.sipfoundry.sipxconfig.phonebook.Address;
import org.sipfoundry.sipxconfig.setting.Group;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class RestUtilities {
    public static final String EMPTY_STRING = "";
    public static final String ID = "id";

    public static final String REQUEST_ATTRIBUTE_ID = ID;
    public static final String REQUEST_ATTRIBUTE_NAME = "name";
    public static final String REQUEST_ATTRIBUTE_BRANCH = "branch";
    public static final String REQUEST_ATTRIBUTE_IDLIST = "ids";

    public static final String ELEMENT_DATA = "data";
    public static final String ELEMENT_ID = ID;
    public static final String ELEMENT_RESPONSE = "response";
    public static final String ELEMENT_ADDITIONAL_MESSAGE = "additionalMessage";
    public static final String ELEMENT_CODE = "code";
    public static final String ELEMENT_MESSAGE = "message";

    public static enum ResponseCode {
        SUCCESS, SUCCESS_CREATED, SUCCESS_UPDATED, SUCCESS_DELETED, ERROR_MISSING_ID, ERROR_BAD_ID,
        ERROR_BAD_INPUT, ERROR_UPDATE_FAILED, ERROR_CREATE_FAILED, ERROR_READ_FAILED, ERROR_REFERENCE_EXISTS
    }

    private RestUtilities() {
        // hide default constructor
    }

    public static int getIntFromAttribute(String attributeString) throws ResourceException {
        int intFromAttribute;

        // attempt to parse attribute provided as an id
        try {
            intFromAttribute = Integer.parseInt(attributeString);
        } catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Attribute " + attributeString
                    + " invalid.");
        }

        return intFromAttribute;
    }

    public static PaginationInfo calculatePagination(Form form, int totalResults) {
        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setTotalResults(totalResults);

        // must specify both PageNumber and ResultsPerPage together
        String pageNumberString = form.getFirstValue("page");
        String resultsPerPageString = form.getFirstValue("pagesize");

        // attempt to parse pagination values from request
        try {
            paginationInfo.setPageNumber(Integer.parseInt(pageNumberString));
            paginationInfo.setResultsPerPage(Integer.parseInt(resultsPerPageString));
        } catch (Exception exception) {
            // default 0 for nothing
            paginationInfo.setPageNumber(0);
            paginationInfo.setResultsPerPage(0);
        }

        // check for outrageous values or lack of parameters
        if ((paginationInfo.getPageNumber() < 1) || (paginationInfo.getResultsPerPage() < 1)) {
            paginationInfo.setPageNumber(0);
            paginationInfo.setResultsPerPage(0);
            paginationInfo.setPaginate(false);
        } else {
            paginationInfo.setPaginate(true);
        }

        // do we have to paginate?
        if (paginationInfo.getPaginate()) {
            paginationInfo.setTotalPages(((paginationInfo.getTotalResults() - 1) / paginationInfo
                    .getResultsPerPage()) + 1);

            // check if only one page
            // if (resultsPerPage >= totalResults) {
            if (paginationInfo.getTotalPages() == 1) {
                paginationInfo.setStartIndex(0);
                paginationInfo.setEndIndex(paginationInfo.getTotalResults() - 1);
                paginationInfo.setPageNumber(1);
                // design decision: should the resultsPerPage actually be set to totalResults?
                // since totalResults are already available preserve call value
            } else {
                // check if specified page number is on or beyoned last page (then use last page)
                if (paginationInfo.getPageNumber() >= paginationInfo.getTotalPages()) {
                    paginationInfo.setPageNumber(paginationInfo.getTotalPages());
                    paginationInfo.setStartIndex((paginationInfo.getTotalPages() - 1)
                            * paginationInfo.getResultsPerPage());
                    paginationInfo.setEndIndex(paginationInfo.getTotalResults() - 1);
                } else {
                    paginationInfo.setStartIndex((paginationInfo.getPageNumber() - 1)
                            * paginationInfo.getResultsPerPage());
                    paginationInfo.setEndIndex(paginationInfo.getStartIndex()
                            + paginationInfo.getResultsPerPage() - 1);
                }
            }
        } else {
            // default values assuming no pagination
            paginationInfo.setStartIndex(0);
            paginationInfo.setEndIndex(paginationInfo.getTotalResults() - 1);
            paginationInfo.setPageNumber(1);
            paginationInfo.setTotalPages(1);
            paginationInfo.setResultsPerPage(paginationInfo.getTotalResults());
        }

        return paginationInfo;
    }

    public static SortInfo calculateSorting(Form form) {
        SortInfo sortInfo = new SortInfo();

        String sortDirectionString = form.getFirstValue("sortdir");
        String sortFieldString = form.getFirstValue("sortby");

        // check for invalid input
        if ((sortDirectionString == null) || (sortFieldString == null)) {
            sortInfo.setSort(false);
            return sortInfo;
        }

        if ((sortDirectionString.isEmpty()) || (sortFieldString.isEmpty())) {
            sortInfo.setSort(false);
            return sortInfo;
        }

        sortInfo.setSort(true);

        // assume forward if get anything else but "reverse"
        if (sortDirectionString.toLowerCase().equals("reverse")) {
            sortInfo.setDirectionForward(false);
        } else {
            sortInfo.setDirectionForward(true);
        }

        // tough to type-check this one
        sortInfo.setSortField(sortFieldString);

        return sortInfo;
    }

    public static int compareIgnoreCaseNullSafe(String left, String right) {
        String leftString = left;
        String rightString = right;

        if (leftString == null) {
            leftString = EMPTY_STRING;
        }

        if (rightString == null) {
            rightString = EMPTY_STRING;
        }

        return leftString.compareToIgnoreCase(rightString);
    }

    // XML Response functions
    // ----------------------

    public static void setResponse(Response response, ResponseCode code, String message) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement(ELEMENT_RESPONSE);
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);

            // no related data (create function overloads to modify)

            response.setEntity(new DomRepresentation(MediaType.TEXT_XML, doc));

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void setResponse(Response response, ResponseCode code, String message, int id) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement(ELEMENT_RESPONSE);
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);

            // add related data
            Element elementData = doc.createElement(ELEMENT_DATA);
            Element elementId = doc.createElement(ELEMENT_ID);
            elementId.appendChild(doc.createTextNode(String.valueOf(id)));
            elementData.appendChild(elementId);
            elementResponse.appendChild(elementData);

            response.setEntity(new DomRepresentation(MediaType.TEXT_XML, doc));

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void setResponse(Response response, ResponseCode code, String message, String id) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement(ELEMENT_RESPONSE);
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);

            // add related data
            Element elementData = doc.createElement(ELEMENT_DATA);
            Element elementId = doc.createElement(ELEMENT_ID);
            elementId.appendChild(doc.createTextNode(id));
            elementData.appendChild(elementId);
            elementResponse.appendChild(elementData);

            response.setEntity(new DomRepresentation(MediaType.TEXT_XML, doc));

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void setResponseError(Response response, ResponseCode code, String message) {
        Representation representation = getResponseError(response, code, message);

        response.setEntity(representation);
    }

    public static Representation getResponseError(Response response, ResponseCode code, String message) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement(ELEMENT_RESPONSE);
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);

            return representation; // new DomRepresentation(MediaType.TEXT_XML, doc);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    public static void setResponseError(Response response, ResponseCode code, String message,
            String additionalMessage) {
        Representation representation = getResponseError(response, code, message, additionalMessage);

        response.setEntity(representation);
    }

    public static Representation getResponseError(Response response, ResponseCode code, String message,
            String additionalMessage) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement(ELEMENT_RESPONSE);
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);

            // add related data
            Element elementData = doc.createElement(ELEMENT_DATA);
            Element elementId = doc.createElement(ELEMENT_ADDITIONAL_MESSAGE);
            elementId.appendChild(doc.createTextNode(additionalMessage));
            elementData.appendChild(elementId);
            elementResponse.appendChild(elementData);

            return representation; // new DomRepresentation(MediaType.TEXT_XML, doc);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    private static void setResponseHeader(Document doc, Element elementResponse, ResponseCode code,
            String message) {

        // add standard elements
        Element elementCode = doc.createElement(ELEMENT_CODE);
        elementCode.appendChild(doc.createTextNode(code.toString()));
        elementResponse.appendChild(elementCode);

        Element elementMessage = doc.createElement(ELEMENT_MESSAGE);
        elementMessage.appendChild(doc.createTextNode(message));
        elementResponse.appendChild(elementMessage);
    }

    public static String getResponseMessage(ResponseCode responseCode, String content) {
        String errorMessage;

        switch (responseCode) {
        case ERROR_BAD_ID:
            errorMessage = "ID " + content + " not found";
            break;

        case ERROR_MISSING_ID:
            errorMessage = "ID value missing from request: " + content;
            break;

        case ERROR_READ_FAILED:
            errorMessage = "Read failed: " + content;
            break;

        case ERROR_UPDATE_FAILED:
            errorMessage = "Update failed: " + content;
            break;

        case ERROR_CREATE_FAILED:
            errorMessage = "Create failed: " + content;
            break;

        case ERROR_BAD_INPUT:
            errorMessage = "Input is invalid" + content;
            break;

        case ERROR_REFERENCE_EXISTS:
            errorMessage = "Reference to object still exists: " + content;
            break;

        case SUCCESS_UPDATED:
            errorMessage = "Updated: " + content;
            break;

        case SUCCESS_CREATED:
            errorMessage = "Created: " + content;
            break;

        case SUCCESS_DELETED:
            errorMessage = "Deleted: " + content;
            break;

        default:
            errorMessage = "Unhandled messageType: " + content;
            break;
        }

        return errorMessage;
    }

    private static void setResponseStatus(Response response, ResponseCode code) {
        // set response status based on code
        switch (code) {
        case SUCCESS_CREATED:
            response.setStatus(Status.SUCCESS_CREATED);
            break;

        case SUCCESS_DELETED:
            response.setStatus(Status.SUCCESS_OK);
            break;

        case SUCCESS_UPDATED:
            response.setStatus(Status.SUCCESS_OK);
            break;

        case ERROR_MISSING_ID:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;

        case ERROR_BAD_ID:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;

        case ERROR_UPDATE_FAILED:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;

        case ERROR_CREATE_FAILED:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;

        case ERROR_READ_FAILED:
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            break;

        case ERROR_BAD_INPUT:
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            break;

        case ERROR_REFERENCE_EXISTS:
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            break;

        default:
            response.setStatus(Status.SUCCESS_OK);
        }
    }

    // Data objects
    // ------------

    public static class PaginationInfo {
        private Boolean m_paginate = false;
        private int m_pageNumber;
        private int m_resultsPerPage;
        private int m_totalPages;
        private int m_totalResults;
        private int m_startIndex;
        private int m_endIndex;

        Boolean getPaginate() {
            return m_paginate;
        }

        void setPaginate(Boolean paginate) {
            this.m_paginate = paginate;
        }

        int getPageNumber() {
            return m_pageNumber;
        }

        void setPageNumber(int pageNumber) {
            this.m_pageNumber = pageNumber;
        }

        int getResultsPerPage() {
            return m_resultsPerPage;
        }

        void setResultsPerPage(int resultsPerPage) {
            this.m_resultsPerPage = resultsPerPage;
        }

        int getTotalPages() {
            return m_totalPages;
        }

        void setTotalPages(int totalPages) {
            this.m_totalPages = totalPages;
        }

        int getTotalResults() {
            return m_totalResults;
        }

        void setTotalResults(int totalResults) {
            this.m_totalResults = totalResults;
        }

        int getStartIndex() {
            return m_startIndex;
        }

        void setStartIndex(int startIndex) {
            this.m_startIndex = startIndex;
        }

        int getEndIndex() {
            return m_endIndex;
        }

        void setEndIndex(int endIndex) {
            this.m_endIndex = endIndex;
        }
    }

    public static class SortInfo {
        private Boolean m_sort = false;
        private Boolean m_directionForward = true;
        private String m_sortField = EMPTY_STRING;

        Boolean getSort() {
            return m_sort;
        }

        void setSort(Boolean sort) {
            this.m_sort = sort;
        }

        Boolean getDirectionForward() {
            return m_directionForward;
        }

        void setDirectionForward(Boolean directionForward) {
            this.m_directionForward = directionForward;
        }

        String getSortField() {
            return m_sortField;
        }

        void setSortField(String sortField) {
            this.m_sortField = sortField;
        }
    }

    public static class ValidationInfo {
        private Boolean m_valid = true;
        private String m_message = "Valid";
        private ResponseCode m_responseCode = ResponseCode.SUCCESS;

        Boolean getValid() {
            return m_valid;
        }

        void setValid(Boolean valid) {
            this.m_valid = valid;
        }

        String getMessage() {
            return m_message;
        }

        void setMessage(String message) {
            this.m_message = message;
        }

        ResponseCode getResponseCode() {
            return m_responseCode;
        }

        void setResponseCode(ResponseCode responseCode) {
            this.m_responseCode = responseCode;
        }
    }

    // Common Rest Info objects
    // ------------------------

    static class PermissionRestInfoFull {
        private final String m_name;
        private final String m_label;
        private final String m_description;
        private final boolean m_defaultValue;
        private final Permission.Type m_type;
        private final boolean m_builtIn;

        public PermissionRestInfoFull(Permission permission) {
            m_name = permission.getName();
            m_label = permission.getLabel();
            m_description = permission.getDescription();
            m_defaultValue = permission.getDefaultValue();
            m_type = permission.getType();
            m_builtIn = permission.isBuiltIn();
        }

        public String getName() {
            return m_name;
        }

        public String getLabel() {
            return m_label;
        }

        public String getDescription() {
            return m_description;
        }

        public boolean getDefaultValue() {
            return m_defaultValue;
        }

        public Permission.Type getType() {
            return m_type;
        }

        public boolean getBuiltIn() {
            return m_builtIn;
        }
    }

    static class BranchRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;

        public BranchRestInfo(Branch branch) {
            m_id = branch.getId();
            m_name = branch.getName();
            m_description = branch.getDescription();
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }
    }

    static class BranchRestInfoFull extends BranchRestInfo {
        private final Address m_address;
        private final String m_phoneNumber;
        private final String m_faxNumber;

        public BranchRestInfoFull(Branch branch) {
            super(branch);

            m_address = branch.getAddress();
            m_phoneNumber = branch.getPhoneNumber();
            m_faxNumber = branch.getFaxNumber();
        }

        public Address getAddress() {
            return m_address;
        }

        public String getPhoneNumber() {
            return m_phoneNumber;
        }

        public String getFaxNumber() {
            return m_faxNumber;
        }
    }

    static class UserGroupRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;

        public UserGroupRestInfo(Group userGroup) {
            m_id = userGroup.getId();
            m_name = userGroup.getName();
            m_description = userGroup.getDescription();
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }
    }

    static class UserGroupRestInfoFull extends UserGroupRestInfo {
        private final BranchRestInfoFull m_branch;

        public UserGroupRestInfoFull(Group userGroup, BranchRestInfoFull branchRestInfo) {
            super(userGroup);

            m_branch = branchRestInfo;
        }

        public BranchRestInfoFull getBranch() {
            return m_branch;
        }
    }

    static class SettingPermissionRestInfo {
        private final String m_name;
        private final String m_label;
        private final String m_value;
        private final boolean m_defaultValue;

        public SettingPermissionRestInfo(String name, String label, String value, boolean defaultValue) {
            m_name = name;
            m_label = label;
            m_value = value;
            m_defaultValue = defaultValue;
        }

        public String getName() {
            return m_name;
        }

        public String getLabel() {
            return m_label;
        }

        public String getValue() {
            return m_value;
        }

        public boolean getDefaultValue() {
            return m_defaultValue;
        }
    }

    static class UserGroupPermissionRestInfoFull extends UserGroupRestInfo {
        private final List<SettingPermissionRestInfo> m_permissions;

        public UserGroupPermissionRestInfoFull(Group userGroup,
                List<SettingPermissionRestInfo> settingsRestInfo) {
            super(userGroup);

            m_permissions = settingsRestInfo;
        }

        public List<SettingPermissionRestInfo> getPermissions() {
            return m_permissions;
        }
    }

    static class UserRestInfoFull {
        private final int m_id;
        private final String m_userName; // also called "User ID" in gui
        private final String m_lastName;
        private final String m_firstName;
        private final String m_pin;
        private final String m_sipPassword;
        private final String m_emailAddress; // this is actually from "Contact Information" tab.
                                             // Maybe create separate API later
        private final List<UserGroupRestInfo> m_groups;
        private final BranchRestInfo m_branch;
        private final List<AliasRestInfo> m_aliases;

        public UserRestInfoFull(User user, List<UserGroupRestInfo> userGroupsRestInfo,
                BranchRestInfo branchRestInfo, List<AliasRestInfo> aliasesRestInfo) {
            m_id = user.getId();
            m_userName = user.getUserName();
            m_lastName = user.getLastName();
            m_firstName = user.getFirstName();
            m_pin = EMPTY_STRING; // pin is hardcoded to never display but must still be submitted
            m_sipPassword = user.getSipPassword();
            m_emailAddress = user.getEmailAddress();
            m_groups = userGroupsRestInfo;
            m_branch = branchRestInfo;
            m_aliases = aliasesRestInfo;
        }

        public int getId() {
            return m_id;
        }

        public String getUserName() {
            return m_userName;
        }

        public String getLastName() {
            return m_lastName;
        }

        public String getFirstName() {
            return m_firstName;
        }

        public String getPin() {
            return m_pin;
        }

        public String getSipPassword() {
            return m_sipPassword;
        }

        public String getEmailAddress() {
            return m_emailAddress;
        }

        public List<UserGroupRestInfo> getGroups() {
            return m_groups;
        }

        public BranchRestInfo getBranch() {
            return m_branch;
        }

        public List<AliasRestInfo> getAliases() {
            return m_aliases;
        }
    }

    static class AliasRestInfo {

        private final String m_alias;

        public AliasRestInfo(String alias) {
            m_alias = alias;
        }

        public String getAlias() {
            return m_alias;
        }
    }

    // Common OpenACD Rest Info objects
    // ------------------------

    static class MetadataRestInfo {
        private final int m_totalResults;
        private final int m_currentPage;
        private final int m_totalPages;
        private final int m_resultsPerPage;

        public MetadataRestInfo(PaginationInfo paginationInfo) {
            m_totalResults = paginationInfo.getTotalResults();
            m_currentPage = paginationInfo.getPageNumber();
            m_totalPages = paginationInfo.getTotalPages();
            m_resultsPerPage = paginationInfo.getResultsPerPage();
        }

        public int getTotalResults() {
            return m_totalResults;
        }

        public int getCurrentPage() {
            return m_currentPage;
        }

        public int getTotalPages() {
            return m_totalPages;
        }

        public int getResultsPerPage() {
            return m_resultsPerPage;
        }
    }

    static class OpenAcdSkillRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;
        private final String m_groupName;

        public OpenAcdSkillRestInfo(OpenAcdSkill skill) {
            m_id = skill.getId();
            m_name = skill.getName();
            m_description = skill.getDescription();
            m_groupName = skill.getGroupName();
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }

        public String getGroupName() {
            return m_groupName;
        }
    }

    static class OpenAcdSkillRestInfoFull extends OpenAcdSkillRestInfo {
        private final String m_atom;
        private final int m_groupId;

        public OpenAcdSkillRestInfoFull(OpenAcdSkill skill) {
            super(skill);
            m_atom = skill.getAtom();
            m_groupId = skill.getGroup().getId();
        }

        public String getAtom() {
            return m_atom;
        }

        public int getGroupId() {
            return m_groupId;
        }
    }

    static class OpenAcdSkillGroupRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;

        public OpenAcdSkillGroupRestInfo(OpenAcdSkillGroup skillGroup) {
            m_id = skillGroup.getId();
            m_name = skillGroup.getName();
            m_description = skillGroup.getDescription();
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }
    }

    static class OpenAcdQueueRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;
        private final String m_groupName;

        public OpenAcdQueueRestInfo(OpenAcdQueue queue) {
            m_id = queue.getId();
            m_name = queue.getName();
            m_description = queue.getDescription();
            m_groupName = queue.getQueueGroup();
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }

        public String getGroupName() {
            return m_groupName;
        }
    }

    static class OpenAcdQueueRestInfoFull extends OpenAcdQueueRestInfo {
        private final int m_groupId;
        private final int m_weight;
        private final List<OpenAcdSkillRestInfo> m_skills;
        private final List<OpenAcdAgentGroupRestInfo> m_agentGroups;
        private final List<OpenAcdRecipeStepRestInfo> m_steps;

        public OpenAcdQueueRestInfoFull(OpenAcdQueue queue, List<OpenAcdSkillRestInfo> skills,
                List<OpenAcdAgentGroupRestInfo> agentGroups, List<OpenAcdRecipeStepRestInfo> steps) {
            super(queue);
            m_groupId = queue.getGroup().getId();
            m_weight = queue.getWeight();
            m_skills = skills;
            m_agentGroups = agentGroups;
            m_steps = steps;
        }

        public int getGroupId() {
            return m_groupId;
        }

        public int getWeight() {
            return m_weight;
        }

        public List<OpenAcdSkillRestInfo> getSkills() {
            return m_skills;
        }

        public List<OpenAcdAgentGroupRestInfo> getAgentGroups() {
            return m_agentGroups;
        }

        public List<OpenAcdRecipeStepRestInfo> getSteps() {
            return m_steps;
        }
    }

    static class OpenAcdQueueGroupRestInfoFull {
        private final String m_name;
        private final int m_id;
        private final String m_description;
        private final List<OpenAcdSkillRestInfo> m_skills;
        private final List<OpenAcdAgentGroupRestInfo> m_agentGroups;
        private final List<OpenAcdRecipeStepRestInfo> m_steps;

        public OpenAcdQueueGroupRestInfoFull(OpenAcdQueueGroup queueGroup, List<OpenAcdSkillRestInfo> skills,
                List<OpenAcdAgentGroupRestInfo> agentGroups, List<OpenAcdRecipeStepRestInfo> steps) {
            m_name = queueGroup.getName();
            m_id = queueGroup.getId();
            m_description = queueGroup.getDescription();
            m_skills = skills;
            m_agentGroups = agentGroups;
            m_steps = steps;
        }

        public String getName() {
            return m_name;
        }

        public int getId() {
            return m_id;
        }

        public String getDescription() {
            return m_description;
        }

        public List<OpenAcdSkillRestInfo> getSkills() {
            return m_skills;
        }

        public List<OpenAcdAgentGroupRestInfo> getAgentGroups() {
            return m_agentGroups;
        }

        public List<OpenAcdRecipeStepRestInfo> getSteps() {
            return m_steps;
        }
    }

    static class OpenAcdRecipeActionRestInfo {
        private final String m_action;
        private final String m_actionValue;
        private final List<OpenAcdSkillRestInfo> m_skills;

        public OpenAcdRecipeActionRestInfo(OpenAcdRecipeAction action, List<OpenAcdSkillRestInfo> skills) {
            m_action = action.getAction();
            m_actionValue = action.getActionValue();
            m_skills = skills;
        }

        public String getAction() {
            return m_action;
        }

        public String getActionValue() {
            return m_actionValue;
        }

        public List<OpenAcdSkillRestInfo> getSkills() {
            return m_skills;
        }
    }

    static class OpenAcdRecipeStepRestInfo {
        private final int m_id;
        private final List<OpenAcdRecipeConditionRestInfo> m_conditions;
        private final OpenAcdRecipeActionRestInfo m_action;
        private final String m_frequency;

        public OpenAcdRecipeStepRestInfo(OpenAcdRecipeStep step,
                OpenAcdRecipeActionRestInfo recipeActionRestInfo,
                List<OpenAcdRecipeConditionRestInfo> conditions) {
            m_id = step.getId();
            m_conditions = conditions;
            m_action = recipeActionRestInfo;
            m_frequency = step.getFrequency();
        }

        public int getId() {
            return m_id;
        }

        public List<OpenAcdRecipeConditionRestInfo> getConditions() {
            return m_conditions;
        }

        public OpenAcdRecipeActionRestInfo getAction() {
            return m_action;
        }

        public String getFrequency() {
            return m_frequency;
        }
    }

    static class OpenAcdRecipeConditionRestInfo {
        private final String m_condition;
        private final String m_relation;
        private final String m_valueCondition;

        public OpenAcdRecipeConditionRestInfo(OpenAcdRecipeCondition condition) {
            m_condition = condition.getCondition();
            m_relation = condition.getRelation();
            m_valueCondition = condition.getValueCondition();
        }

        public String getCondition() {
            return m_condition;
        }

        public String getRelation() {
            return m_relation;
        }

        public String getValueCondition() {
            return m_valueCondition;
        }
    }

    static class OpenAcdClientRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;
        private final String m_identity;

        public OpenAcdClientRestInfo(OpenAcdClient client) {
            m_id = client.getId();
            m_name = client.getName();
            m_description = client.getDescription();
            m_identity = client.getIdentity();
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }

        public String getIdentity() {
            return m_identity;
        }
    }

    static class OpenAcdAgentGroupRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;

        public OpenAcdAgentGroupRestInfo(OpenAcdAgentGroup agentGroup) {
            m_id = agentGroup.getId();
            m_name = agentGroup.getName();
            m_description = agentGroup.getDescription();
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }
    }

    static class OpenAcdAgentGroupRestInfoFull extends OpenAcdAgentGroupRestInfo {
        private final List<OpenAcdSkillRestInfo> m_skills;
        private final List<OpenAcdQueueRestInfo> m_queues;
        private final List<OpenAcdClientRestInfo> m_clients;

        public OpenAcdAgentGroupRestInfoFull(OpenAcdAgentGroup agentGroup, List<OpenAcdSkillRestInfo> skills,
                List<OpenAcdQueueRestInfo> queues, List<OpenAcdClientRestInfo> clients) {
            super(agentGroup);
            m_skills = skills;
            m_queues = queues;
            m_clients = clients;
        }

        public List<OpenAcdSkillRestInfo> getSkills() {
            return m_skills;
        }

        public List<OpenAcdQueueRestInfo> getQueues() {
            return m_queues;
        }

        public List<OpenAcdClientRestInfo> getClients() {
            return m_clients;
        }
    }

    static class OpenAcdReleaseCodeRestInfo {
        private final int m_id;
        private final String m_label;
        private final String m_description;
        private final int m_bias;

        public OpenAcdReleaseCodeRestInfo(OpenAcdReleaseCode releaseCode) {
            m_id = releaseCode.getId();
            m_label = releaseCode.getLabel();
            m_bias = releaseCode.getBias();
            m_description = releaseCode.getDescription();
        }

        public int getId() {
            return m_id;
        }

        public String getLabel() {
            return m_label;
        }

        public String getDescription() {
            return m_description;
        }

        public int getBias() {
            return m_bias;
        }
    }

    static class OpenAcdAgentRestInfoFull {

        private final int m_id;
        private final int m_userId;
        private final String m_userName;
        private final String m_firstName;
        private final String m_lastName;
        private final int m_groupId;
        private final String m_groupName;
        private final String m_security;
        private final List<OpenAcdSkillRestInfo> m_skills;
        private final List<OpenAcdQueueRestInfo> m_queues;
        private final List<OpenAcdClientRestInfo> m_clients;

        public OpenAcdAgentRestInfoFull(OpenAcdAgent agent, List<OpenAcdSkillRestInfo> skills,
                List<OpenAcdQueueRestInfo> queues, List<OpenAcdClientRestInfo> clients) {
            m_id = agent.getId();
            m_firstName = agent.getFirstName();
            m_lastName = agent.getLastName();
            m_userId = agent.getUser().getId();
            m_userName = agent.getUser().getName();
            m_groupId = agent.getGroup().getId();
            m_groupName = agent.getGroup().getName();
            m_security = agent.getSecurity();
            m_skills = skills;
            m_queues = queues;
            m_clients = clients;
        }

        public int getId() {
            return m_id;
        }

        public String getFirstName() {
            return m_firstName;
        }

        public String getLastName() {
            return m_lastName;
        }

        public int getUserId() {
            return m_userId;
        }

        public String getUserName() {
            return m_userName;
        }

        public int getGroupId() {
            return m_groupId;
        }

        public String getGroupName() {
            return m_groupName;
        }

        public String getSecurity() {
            return m_security;
        }

        public List<OpenAcdSkillRestInfo> getSkills() {
            return m_skills;
        }

        public List<OpenAcdQueueRestInfo> getQueues() {
            return m_queues;
        }

        public List<OpenAcdClientRestInfo> getClients() {
            return m_clients;
        }
    }

}
