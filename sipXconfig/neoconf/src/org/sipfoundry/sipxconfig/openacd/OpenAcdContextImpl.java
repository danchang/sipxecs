/**
 *
 *
 * Copyright (c) 2010 / 2011 eZuce, Inc. All rights reserved.
 * Contributed to SIPfoundry under a Contributor Agreement
 *
 * This software is free software; you can redistribute it and/or modify it under
 * the terms of the Affero General Public License (AGPL) as published by the
 * Free Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 */
package org.sipfoundry.sipxconfig.openacd;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.sipfoundry.sipxconfig.address.Address;
import org.sipfoundry.sipxconfig.address.AddressManager;
import org.sipfoundry.sipxconfig.address.AddressProvider;
import org.sipfoundry.sipxconfig.address.AddressType;
import org.sipfoundry.sipxconfig.alias.AliasManager;
import org.sipfoundry.sipxconfig.common.BeanId;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.DaoUtils;
import org.sipfoundry.sipxconfig.common.ExtensionInUseException;
import org.sipfoundry.sipxconfig.common.NameInUseException;
import org.sipfoundry.sipxconfig.common.SipxHibernateDaoSupport;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.common.UserException;
import org.sipfoundry.sipxconfig.common.event.DaoEventListener;
import org.sipfoundry.sipxconfig.commserver.Location;
import org.sipfoundry.sipxconfig.commserver.imdb.ReplicationManager;
import org.sipfoundry.sipxconfig.feature.Bundle;
import org.sipfoundry.sipxconfig.feature.FeatureChangeRequest;
import org.sipfoundry.sipxconfig.feature.FeatureChangeValidator;
import org.sipfoundry.sipxconfig.feature.FeatureManager;
import org.sipfoundry.sipxconfig.feature.FeatureProvider;
import org.sipfoundry.sipxconfig.feature.GlobalFeature;
import org.sipfoundry.sipxconfig.feature.LocationFeature;
import org.sipfoundry.sipxconfig.firewall.DefaultFirewallRule;
import org.sipfoundry.sipxconfig.firewall.FirewallManager;
import org.sipfoundry.sipxconfig.firewall.FirewallProvider;
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchAction;
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchCondition;
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchFeature;
import org.sipfoundry.sipxconfig.setting.BeanWithSettingsDao;
import org.sipfoundry.sipxconfig.snmp.ProcessDefinition;
import org.sipfoundry.sipxconfig.snmp.ProcessProvider;
import org.sipfoundry.sipxconfig.snmp.SnmpManager;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.dao.support.DataAccessUtils;

public class OpenAcdContextImpl extends SipxHibernateDaoSupport implements OpenAcdContext, BeanFactoryAware,
        FeatureProvider, AddressProvider, ProcessProvider, DaoEventListener, FirewallProvider {

    private static final String VALUE = "value";
    private static final String OPEN_ACD_EXTENSION_WITH_NAME = "openAcdExtensionWithName";
    private static final String OPEN_ACD_AGENT_GROUP_WITH_NAME = "openAcdAgentGroupWithName";
    private static final String OPEN_ACD_SKILL_GROUPWITH_NAME = "openAcdSkillGroupWithName";
    private static final String OPEN_ACD_SKILL_WITH_NAME = "openAcdSkillWithName";
    private static final String OPEN_ACD_SKILL_WITH_ATOM = "openAcdSkillWithAtom";
    private static final String OPEN_ACD_CLIENT_WITH_NAME = "openAcdClientWithName";
    private static final String OPEN_ACD_CLIENT_WITH_IDENTITY = "openAcdClientWithIdentity";
    private static final String DEFAULT_OPEN_ACD_SKILLS = "defaultOpenAcdSkills";
    private static final String OPEN_ACD_AGENT_BY_USERID = "openAcdAgentByUserId";
    private static final String LINE_NAME = "line";
    private static final String OPEN_ACD_QUEUE_GROUP_WITH_NAME = "openAcdQueueGroupWithName";
    private static final String OPEN_ACD_QUEUE_WITH_NAME = "openAcdQueueWithName";
    private static final String DEFAULT_QUEUE = "default_queue";
    private static final String FS_ACTIONS_WITH_DATA = "freeswitchActionsWithData";
    private static final String OPEN_ACD_RELEASE_CODE_WITH_LABEL = "openAcdClientReleaseCodeWithLabel";
    private static final String OPEN_ACD_PROCESS_NAME = "openacd";

    private AliasManager m_aliasManager;
    private FeatureManager m_featureManager;
    private BeanWithSettingsDao<OpenAcdSettings> m_settingsDao;
    private ListableBeanFactory m_beanFactory;
    private CoreContext m_coreContext;
    private ReplicationManager m_replicationManager;

    private String m_openacdHome;

    @Override
    public Collection<GlobalFeature> getAvailableGlobalFeatures(FeatureManager featureManager) {
        return null;
    }

    @Override
    public Collection<LocationFeature> getAvailableLocationFeatures(FeatureManager featureManager, Location l) {
        return Collections.singleton(FEATURE);
    }

    @Override
    public Collection<DefaultFirewallRule> getFirewallRules(FirewallManager manager) {
        return Collections.singleton(new DefaultFirewallRule(REST_API));
    }

    @Override
    public Collection<Address> getAvailableAddresses(AddressManager manager, AddressType type, Location requester) {
        if (!type.equals(REST_API)) {
            return null;
        }
        List<Location> locations = manager.getFeatureManager().getLocationsForEnabledFeature(FEATURE);
        List<Address> addresses = new ArrayList<Address>(locations.size());
        for (Location location : locations) {
            addresses.add(new Address(REST_API, location.getAddress(), 5050));
        }
        return addresses;
    }

    public OpenAcdSettings getSettings() {
        return m_settingsDao.findOrCreateOne();
    }

    public void saveSettings(OpenAcdSettings settings) {
        m_settingsDao.upsert(settings);
    }

    public OpenAcdLine newOpenAcdLine() {
        return m_beanFactory.getBean(OpenAcdLine.class);
    }

    public OpenAcdCommand newOpenAcdCommand() {
        return m_beanFactory.getBean(OpenAcdCommand.class);
    }

    @Override
    public OpenAcdExtension getExtensionById(Integer extensionId) {
        return getHibernateTemplate().load(OpenAcdExtension.class, extensionId);
    }

    @Override
    public OpenAcdExtension getExtensionByName(String extensionName) {
        List<OpenAcdLine> extensions = getHibernateTemplate().findByNamedQueryAndNamedParam(
                OPEN_ACD_EXTENSION_WITH_NAME, VALUE, extensionName);
        return DataAccessUtils.singleResult(extensions);
    }

    @Override
    public List<OpenAcdExtension> getFreeswitchExtensions() {
        return getHibernateTemplate().loadAll(OpenAcdExtension.class);
    }

    @Override
    public Set<OpenAcdLine> getLines() {
        List<OpenAcdLine> openacdLines = getHibernateTemplate().loadAll(OpenAcdLine.class);
        Set<OpenAcdLine> lines = new HashSet<OpenAcdLine>();
        for (OpenAcdLine ext : openacdLines) {
            if (ext.getExtension() != null) {
                lines.add(ext);
            }
        }
        return lines;
    }

    public Set<OpenAcdCommand> getCommands() {
        List<OpenAcdCommand> openacdCommands = getHibernateTemplate().loadAll(OpenAcdCommand.class);
        Set<OpenAcdCommand> comms = new HashSet<OpenAcdCommand>();
        for (OpenAcdCommand ext : openacdCommands) {
            if (ext.getExtension() != null) {
                comms.add(ext);
            }
        }
        return comms;
    }

    @Override
    public String[] getOpenAcdApplicationNames() {
        Set<String> applications = new HashSet<String>();
        List<FreeswitchAction> actions = getHibernateTemplate().loadAll(FreeswitchAction.class);
        for (FreeswitchAction freeswitchAction : actions) {
            applications.add(freeswitchAction.getApplication());
        }
        applications.addAll(FreeswitchAction.PredefinedAction.valuesAsStrings());
        return applications.toArray(new String[0]);
    }

    @Override
    public boolean isEnabled() {
        return m_featureManager.isFeatureEnabled(FEATURE);
    }

    public void deleteExtension(OpenAcdExtension ext) {
        getHibernateTemplate().delete(ext);
        getHibernateTemplate().flush();
    }

    public void saveExtension(OpenAcdExtension extension) {
        if (extension.getName() == null) {
            throw new UserException("&null.name");
        }
        if (extension.getExtension() == null) {
            throw new UserException("&null.extension");
        }
        String capturedExt = extension.getCapturedExtension();

        if (!m_aliasManager.canObjectUseAlias(extension, extension.getName())) {
            throw new NameInUseException(LINE_NAME, extension.getName());
        } else if (!m_aliasManager.canObjectUseAlias(extension, capturedExt)) {
            throw new ExtensionInUseException(LINE_NAME, capturedExt);
        } else if (extension.getAlias() != null && !m_aliasManager.canObjectUseAlias(extension, extension.getAlias())) {
            throw new ExtensionInUseException(LINE_NAME, extension.getAlias());
        } else if (extension.getDid() != null && !m_aliasManager.canObjectUseAlias(extension, extension.getDid())) {
            throw new ExtensionInUseException(LINE_NAME, extension.getDid());
        }

        removeNullActions(extension);
        if (extension.isNew()) {
            getHibernateTemplate().saveOrUpdate(extension);
        } else {
            getHibernateTemplate().merge(extension);
        }
    }

    private void removeNullActions(OpenAcdExtension extension) {
        if (extension.getConditions() == null) {
            return;
        }
        for (FreeswitchCondition condition : extension.getConditions()) {
            for (FreeswitchAction action : condition.getActions()) {
                if (action != null && action.getApplication() != null) {
                    condition.addAction(action);
                }
            }
        }
    }

    private List<FreeswitchAction> getActionsByData(String actionData) {
        return getHibernateTemplate().findByNamedQueryAndNamedParam(FS_ACTIONS_WITH_DATA, VALUE, actionData);
    }

    @Override
    public Collection getBeanIdsOfObjectsWithAlias(String alias) {
        Collection<BeanId> bids = new ArrayList<BeanId>();

        List<OpenAcdLine> lines = getHibernateTemplate().loadAll(OpenAcdLine.class);
        for (OpenAcdLine line : lines) {
            if (line.getExtension() != null && (line.getExtension().equals(alias) || line.getName().equals(alias))
                    || (line.getAlias() != null && line.getAlias().equals(alias)) || (line.getDid() != null
                    && line.getDid().equals(alias))) {
                bids.add(new BeanId(line.getId(), OpenAcdLine.class));
            }
        }
        List<OpenAcdCommand> commands = getHibernateTemplate().loadAll(OpenAcdCommand.class);
        for (OpenAcdCommand openAcdCommand : commands) {
            if (openAcdCommand.getExtension() != null && (openAcdCommand.getExtension().equals(alias))
                    || openAcdCommand.getName().equals(alias)) {
                bids.add(new BeanId(openAcdCommand.getId(), OpenAcdCommand.class));
            }
        }
        return bids;
    }

    @Override
    public boolean isAliasInUse(String alias) {
        List<OpenAcdExtension> extensions = getFreeswitchExtensions();
        for (OpenAcdExtension openAcdExtension : extensions) {
            if (openAcdExtension.getExtension() != null
                    && (openAcdExtension.getExtension().equals(alias) || openAcdExtension.getName().equals(alias))) {
                return true;
            }
            if (openAcdExtension.getAlias() != null && openAcdExtension.getAlias().equals(alias)) {
                return true;
            }
            if (openAcdExtension.getDid() != null && openAcdExtension.getDid().equals(alias)) {
                return true;
            }
        }
        return false;
    }

    public List<OpenAcdAgentGroup> getAgentGroups() {
        return getHibernateTemplate().loadAll(OpenAcdAgentGroup.class);
    }

    public OpenAcdAgentGroup getAgentGroupById(Integer agentGroupId) {
        return getHibernateTemplate().load(OpenAcdAgentGroup.class, agentGroupId);
    }

    public OpenAcdAgentGroup getAgentGroupByName(String agentGroupName) {
        List<OpenAcdAgentGroup> agentGroups = getHibernateTemplate().findByNamedQueryAndNamedParam(
                OPEN_ACD_AGENT_GROUP_WITH_NAME, VALUE, agentGroupName);
        return DataAccessUtils.singleResult(agentGroups);
    }

    public void saveAgentGroup(OpenAcdAgentGroup agentGroup) {
        // check if agent group name is empty
        if (StringUtils.isBlank(agentGroup.getName())) {
            throw new UserException("&blank.agentGroupName.error");
        }
        // Check for duplicate names before saving the agent group
        if (agentGroup.isNew() || (!agentGroup.isNew() && isNameChanged(agentGroup))) {
            checkForDuplicateName(agentGroup);
        }

        if (!agentGroup.isNew()) {
            if (isNameChanged(agentGroup)) {
                // don't rename the default group
                OpenAcdAgentGroup defaultAgentGroup = getAgentGroupByName(GROUP_NAME_DEFAULT);
                if (defaultAgentGroup != null && defaultAgentGroup.getId().equals(agentGroup.getId())) {
                    throw new UserException("&msg.err.defaultAgentGroupRename");
                }
            }
            getHibernateTemplate().merge(agentGroup);
        } else {
            getHibernateTemplate().save(agentGroup);
        }
    }

    private boolean isNameChanged(OpenAcdAgentGroup agentGroup) {
        String oldName = getAgentGroupById(agentGroup.getId()).getName();
        agentGroup.setOldName(oldName);
        return !oldName.equals(agentGroup.getName());
    }

    private void checkForDuplicateName(OpenAcdAgentGroup agentGroup) {
        String agentGroupName = agentGroup.getName();
        OpenAcdAgentGroup existingAgentGroup = getAgentGroupByName(agentGroupName);
        if (existingAgentGroup != null) {
            throw new UserException("&duplicate.agentGroupName.error", agentGroupName);
        }
    }

    public void deleteAgentGroup(OpenAcdAgentGroup group) {
        if (group.getName().equals(GROUP_NAME_DEFAULT)) {
            throw new DefaultAgentGroupDeleteException();
        }
        getHibernateTemplate().delete(group);
    }

    public class DefaultAgentGroupDeleteException extends UserException {
    }

    public List<OpenAcdAgent> getAgents() {
        return getHibernateTemplate().loadAll(OpenAcdAgent.class);
    }

    public List<OpenAcdRecipeAction> getRecipeActions() {
        return getHibernateTemplate().loadAll(OpenAcdRecipeAction.class);
    }

    public OpenAcdAgent getAgentById(Integer agentId) {
        return getHibernateTemplate().load(OpenAcdAgent.class, agentId);
    }

    public OpenAcdAgent getAgentByUserId(Integer userId) {
        Collection<OpenAcdAgent> agents = getHibernateTemplate().findByNamedQueryAndNamedParam(
                OPEN_ACD_AGENT_BY_USERID, VALUE, userId);
        return DaoUtils.requireOneOrZero(agents, OPEN_ACD_AGENT_BY_USERID);
    }

    @Override
    public void saveAgent(OpenAcdAgent agent) {
        checkAgent(agent);
        getHibernateTemplate().saveOrUpdate(agent);
        agent.setOldName(agent.getName());
        m_coreContext.saveUserToAgentsGroup(agent.getUser());
    }

    private void checkAgent(OpenAcdAgent agent) {
        // check if agent security is empty
        if (StringUtils.isBlank(agent.getSecurity())) {
            throw new UserException("&blank.agentSecurity.error");
        }
    }

    @Override
    public void deleteAgent(OpenAcdAgent agent) {
        getHibernateTemplate().delete(agent);
        m_coreContext.saveRemoveUserFromAgentGroup(agent.getUser());
    }

    @Override
    public OpenAcdAgent getAgentByUser(User user) {
        return getAgentByUserId(user.getId());
    }

    @Override
    public boolean isOpenAcdAgent(User user) {
        if (getAgentByUser(user) != null) {
            return true;
        }
        return false;
    }

    @Override
    public List<OpenAcdSkillGroup> getSkillGroups() {
        return getHibernateTemplate().loadAll(OpenAcdSkillGroup.class);
    }

    @Override
    public OpenAcdSkillGroup getSkillGroupById(Integer skillGroupId) {
        return getHibernateTemplate().load(OpenAcdSkillGroup.class, skillGroupId);
    }

    @Override
    public OpenAcdSkillGroup getSkillGroupByName(String skillGroupName) {
        List<OpenAcdSkillGroup> skillGroups = getHibernateTemplate().findByNamedQueryAndNamedParam(
                OPEN_ACD_SKILL_GROUPWITH_NAME, VALUE, skillGroupName);
        return DataAccessUtils.singleResult(skillGroups);
    }

    @Override
    public void saveSkillGroup(OpenAcdSkillGroup skillGroup) {
        if (StringUtils.isBlank(skillGroup.getName())) {
            throw new UserException("&blank.skillGroupName.error");
        }
        if (skillGroup.isNew() || (!skillGroup.isNew() && isNameChanged(skillGroup))) {
            checkForDuplicateName(skillGroup);
        }

        if (!skillGroup.isNew()) {
            if (isNameChanged(skillGroup)) {
                // don't rename the Magic skill group
                OpenAcdSkillGroup magicSkillGroup = getSkillGroupByName(MAGIC_SKILL_GROUP_NAME);
                if (magicSkillGroup != null && magicSkillGroup.getId().equals(skillGroup.getId())) {
                    throw new UserException("&msg.err.magicSkillGroupRename");
                }

                List<OpenAcdSkill> skills = new LinkedList<OpenAcdSkill>();
                for (OpenAcdSkill skill : skillGroup.getSkills()) {
                    skills.add(skill);
                }
            }
            getHibernateTemplate().merge(skillGroup);
        } else {
            getHibernateTemplate().save(skillGroup);
        }
    }

    private boolean isNameChanged(OpenAcdSkillGroup skillGroup) {
        String oldName = getSkillGroupById(skillGroup.getId()).getName();
        skillGroup.setOldName(oldName);
        return !oldName.equals(skillGroup.getName());
    }

    private void checkForDuplicateName(OpenAcdSkillGroup skillGroup) {
        String skillGroupName = skillGroup.getName();
        OpenAcdSkillGroup existingSkillGroup = getSkillGroupByName(skillGroupName);
        if (existingSkillGroup != null) {
            throw new UserException("&duplicate.skillGroupName.error", skillGroupName);
        }
    }

    @Override
    public List<String> removeSkillGroups(Collection<Integer> skillGroupIds) {
        List<OpenAcdSkillGroup> groups = new LinkedList<OpenAcdSkillGroup>();
        List<String> usedSkillGroups = new ArrayList<String>();
        for (Integer id : skillGroupIds) {
            OpenAcdSkillGroup group = getSkillGroupById(id);
            if (group.getName().equals(MAGIC_SKILL_GROUP_NAME) || containsUsedSkills(group)) {
                usedSkillGroups.add(group.getName());
            } else {
                groups.add(group);
            }
        }
        getDaoEventPublisher().publishDeleteCollection(groups);
        getHibernateTemplate().deleteAll(groups);

        return usedSkillGroups;
    }

    @Override
    public boolean containsUsedSkills(OpenAcdSkillGroup skillGroup) {
        for (OpenAcdSkill skill : skillGroup.getSkills()) {
            if (skill.isDefaultSkill() || isSkillInUse(skill)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<OpenAcdSkill> getSkills() {
        return getHibernateTemplate().loadAll(OpenAcdSkill.class);
    }

    @Override
    public List<OpenAcdSkill> getDefaultSkills() {
        return getHibernateTemplate().findByNamedQuery(DEFAULT_OPEN_ACD_SKILLS);
    }

    @Override
    public OpenAcdSkill getSkillById(Integer skillId) {
        return getHibernateTemplate().load(OpenAcdSkill.class, skillId);
    }

    @Override
    public OpenAcdSkill getSkillByName(String skillName) {
        List<OpenAcdSkill> skills = getHibernateTemplate().findByNamedQueryAndNamedParam(OPEN_ACD_SKILL_WITH_NAME,
                VALUE, skillName);
        return DataAccessUtils.singleResult(skills);
    }

    @Override
    public OpenAcdSkill getSkillByAtom(String atom) {
        List<OpenAcdSkill> skills = getHibernateTemplate().findByNamedQueryAndNamedParam(OPEN_ACD_SKILL_WITH_ATOM,
                VALUE, atom);
        return DataAccessUtils.singleResult(skills);
    }

    @Override
    public void saveSkill(OpenAcdSkill skill) {
        // Check if skill name is empty
        if (StringUtils.isBlank(skill.getName())) {
            throw new UserException("&blank.skillName.error");
        }
        // Check if skill atom is empty
        if (StringUtils.isBlank(skill.getAtom())) {
            throw new UserException("&blank.skillAtom.error");
        }
        // Check if skill group is empty
        if (skill.getGroup() == null) {
            throw new UserException("&error.requiredSkillGroup");
        }
        // Check for duplicate names before saving the skill
        if (skill.isNew() || (!skill.isNew() && isNameChanged(skill))) {
            checkForDuplicateName(skill);
        }
        // Check for duplicate atoms before saving the skill
        if (skill.isNew() || (!skill.isNew() && isAtomChanged(skill))) {
            checkForDuplicateAtom(skill);
        }
        // Magic skills cannot be added, changed or deleted
        if (skill.getGroupName().equals(MAGIC_SKILL_GROUP_NAME)) {
            throw new UserException("&error.forbiddenMagicSkillGroup");
        }

        if (!skill.isNew()) {
            getHibernateTemplate().merge(skill);
        } else {
            getHibernateTemplate().save(skill);
        }
    }

    public void deleteSkill(OpenAcdSkill skill) {
        List<OpenAcdSkill> skills = new LinkedList<OpenAcdSkill>();
        if (skill.isDefaultSkill() || isSkillInUse(skill)) {
            throw new SkillInUseException();
        } else {
            OpenAcdSkillGroup group = skill.getGroup();
            group.removeSkill(skill);
            getHibernateTemplate().saveOrUpdate(group);
            skills.add(skill);
        }
        getDaoEventPublisher().publishDeleteCollection(skills);
        getHibernateTemplate().deleteAll(skills);
    }

    public class SkillInUseException extends UserException {
    }

    private boolean isSkillInUse(OpenAcdSkill skill) {
        if (countObjectWithSkillId(skill.getId(), "countOpenAcdAgentGroupWithSkill") > 0
                || countObjectWithSkillId(skill.getId(), "countOpenAcdAgentWithSkill") > 0
                || countObjectWithSkillId(skill.getId(), "countOpenAcdQueueGroupWithSkill") > 0
                || countObjectWithSkillId(skill.getId(), "countOpenAcdQueueWithSkill") > 0) {
            return true;
        }
        return false;

    }

    private int countObjectWithSkillId(Integer id, String queryName) {
        List countAgentGroups = getHibernateTemplate().findByNamedQueryAndNamedParam(queryName, new String[] {
            VALUE
        }, new Object[] {
            id
        });

        return DataAccessUtils.intResult(countAgentGroups);
    }

    public Map<String, List<OpenAcdSkill>> getGroupedSkills() {
        return getFilteredGroupedSkills(new ArrayList<String>());
    }

    public Map<String, List<OpenAcdSkill>> getAgentGroupedSkills() {
        List<String> atomsToFilter = new ArrayList<String>();
        atomsToFilter.add("_brand");
        atomsToFilter.add("_queue");
        return getFilteredGroupedSkills(atomsToFilter);
    }

    public Map<String, List<OpenAcdSkill>> getQueueGroupedSkills() {
        List<String> atomsToFilter = new ArrayList<String>();
        atomsToFilter.add("_agent");
        atomsToFilter.add("_profile");
        return getFilteredGroupedSkills(atomsToFilter);
    }

    private Map<String, List<OpenAcdSkill>> getFilteredGroupedSkills(List<String> atomsToFilter) {
        Map<String, List<OpenAcdSkill>> groupedSkills = new TreeMap<String, List<OpenAcdSkill>>();
        List<OpenAcdSkill> skills = getSkills();
        if (!skills.isEmpty()) {
            for (OpenAcdSkill skill : skills) {
                if (!atomsToFilter.contains(skill.getAtom())) {
                    String skillGroupName = skill.getGroupName();
                    if (groupedSkills.containsKey(skillGroupName)) {
                        groupedSkills.get(skillGroupName).add(skill);
                    } else {
                        LinkedList<OpenAcdSkill> groupNameSkills = new LinkedList<OpenAcdSkill>();
                        groupNameSkills.add(skill);
                        groupedSkills.put(skill.getGroupName(), groupNameSkills);
                    }
                }
            }
        }
        return groupedSkills;
    }

    @Override
    public void saveClient(OpenAcdClient client) {
        // Check for duplicate names before saving the skill
        if (client.isNew() || (!client.isNew() && isNameChanged(client))) {
            checkForDuplicateName(client);
        }
        // Check for duplicate atoms before saving the skill
        if (client.isNew() || (!client.isNew() && isIdentityChanged(client))) {
            checkForDuplicateIdentity(client);
        }

        if (client.isNew()) {
            getHibernateTemplate().save(client);
        } else {
            getHibernateTemplate().merge(client);
        }
    }

    private boolean isNameChanged(OpenAcdClient client) {
        return !getClientById(client.getId()).getName().equals(client.getName());
    }

    private boolean isIdentityChanged(OpenAcdClient client) {
        return !getClientById(client.getId()).getIdentity().equals(client.getIdentity());
    }

    private void checkForDuplicateName(OpenAcdClient client) {
        String clientName = client.getName();
        OpenAcdClient existingClient = getClientByName(clientName);
        if (existingClient != null) {
            throw new UserException("&duplicate.clientName.error", existingClient);
        }
    }

    private void checkForDuplicateIdentity(OpenAcdClient client) {
        String identity = client.getIdentity();
        OpenAcdClient existingClient = getClientByIdentity(identity);
        if (existingClient != null) {
            throw new UserException("&duplicate.clientIdentity.error", existingClient);
        }
    }

    public OpenAcdClient getClientByName(String clientName) {
        List<OpenAcdClient> clients = getHibernateTemplate().findByNamedQueryAndNamedParam(
                OPEN_ACD_CLIENT_WITH_NAME, VALUE, clientName);
        return DataAccessUtils.singleResult(clients);
    }

    @Override
    public OpenAcdClient getClientByIdentity(String identity) {
        List<OpenAcdClient> clients = getHibernateTemplate().findByNamedQueryAndNamedParam(
                OPEN_ACD_CLIENT_WITH_IDENTITY, VALUE, identity);
        return DataAccessUtils.singleResult(clients);
    }

    @Override
    public void deleteClient(OpenAcdClient client) {
        if (isUsedByLine(OpenAcdLine.BRAND + client.getIdentity())) {
            throw new ClientInUseException();
        } else {
            getHibernateTemplate().delete(client);
        }
    }

    public final class ClientInUseException extends UserException {
    }

    @Override
    public List<OpenAcdClient> getClients() {
        return getHibernateTemplate().loadAll(OpenAcdClient.class);
    }

    @Override
    public OpenAcdClient getClientById(Integer clientId) {
        return getHibernateTemplate().load(OpenAcdClient.class, clientId);
    }

    private boolean isNameChanged(OpenAcdSkill skill) {
        return !getSkillById(skill.getId()).getName().equals(skill.getName());
    }

    private void checkForDuplicateName(OpenAcdSkill skill) {
        String skillName = skill.getName();
        OpenAcdSkill existingSkill = getSkillByName(skillName);
        if (existingSkill != null) {
            throw new UserException("&duplicate.skillName.error", existingSkill);
        }
    }

    private boolean isAtomChanged(OpenAcdSkill skill) {
        return !getSkillById(skill.getId()).getAtom().equals(skill.getAtom());
    }

    private void checkForDuplicateAtom(OpenAcdSkill skill) {
        String atom = skill.getAtom();
        OpenAcdSkill existingSkill = getSkillByAtom(atom);
        if (existingSkill != null) {
            throw new UserException("&duplicate.skillAtom.error", existingSkill);
        }
    }

    @Override
    public List<OpenAcdQueueGroup> getQueueGroups() {
        return getHibernateTemplate().loadAll(OpenAcdQueueGroup.class);
    }

    @Override
    public OpenAcdQueueGroup getQueueGroupById(Integer queueGroupId) {
        return getHibernateTemplate().load(OpenAcdQueueGroup.class, queueGroupId);
    }

    @Override
    public OpenAcdQueueGroup getQueueGroupByName(String queueGroupName) {
        List<OpenAcdQueueGroup> queueGroups = getHibernateTemplate().findByNamedQueryAndNamedParam(
                OPEN_ACD_QUEUE_GROUP_WITH_NAME, VALUE, queueGroupName);
        return DataAccessUtils.singleResult(queueGroups);
    }

    @Override
    public void saveQueueGroup(OpenAcdQueueGroup queueGroup) {
        // check if queue group name is empty
        if (StringUtils.isBlank(queueGroup.getName())) {
            throw new UserException("&blank.queueGroupName.error");
        }
        // Check for duplicate names before saving the queue group
        if (queueGroup.isNew() || (!queueGroup.isNew() && isNameChanged(queueGroup))) {
            checkForDuplicateName(queueGroup);
        }

        if (!queueGroup.isNew()) {
            if (isNameChanged(queueGroup)) {
                // don't rename the default queue group
                OpenAcdQueueGroup defaultQueueGroup = getQueueGroupByName(GROUP_NAME_DEFAULT);
                if (defaultQueueGroup != null && defaultQueueGroup.getId().equals(queueGroup.getId())) {
                    throw new UserException("&msg.err.defaultQueueGroupRename");
                }
            }
            getHibernateTemplate().merge(queueGroup);
        } else {
            getHibernateTemplate().save(queueGroup);
        }
    }

    @Override
    public void deleteQueueGroup(OpenAcdQueueGroup group) {
        List<OpenAcdQueueGroup> groups = new LinkedList<OpenAcdQueueGroup>();
        if (group.getName().equals(GROUP_NAME_DEFAULT) || containsUsedQueues(group)) {
            throw new QueueGroupInUseException();
        } else {
            groups.add(group);
        }
        getHibernateTemplate().deleteAll(groups);
    }

    public final class QueueGroupInUseException extends UserException {
    }

    private boolean isNameChanged(OpenAcdQueueGroup queueGroup) {
        String oldName = getQueueGroupById(queueGroup.getId()).getName();
        queueGroup.setOldName(oldName);
        return !oldName.equals(queueGroup.getName());
    }

    private void checkForDuplicateName(OpenAcdQueueGroup queueGroup) {
        String queueGroupName = queueGroup.getName();
        OpenAcdQueueGroup existingQueueGroup = getQueueGroupByName(queueGroupName);
        if (existingQueueGroup != null) {
            throw new UserException("&duplicate.queueGroupName.error", queueGroupName);
        }
    }

    private boolean containsUsedQueues(OpenAcdQueueGroup group) {
        for (OpenAcdQueue queue : group.getQueues()) {
            if (isUsedByLine(OpenAcdLine.Q + queue.getName())) {
                return true;
            }
        }
        return false;
    }

    public List<OpenAcdQueue> getQueues() {
        return getHibernateTemplate().loadAll(OpenAcdQueue.class);
    }

    public OpenAcdQueue getQueueById(Integer queueId) {
        return getHibernateTemplate().load(OpenAcdQueue.class, queueId);
    }

    public OpenAcdQueue getQueueByName(String queueName) {
        List<OpenAcdQueue> queues = getHibernateTemplate().findByNamedQueryAndNamedParam(OPEN_ACD_QUEUE_WITH_NAME,
                VALUE, queueName);
        return DataAccessUtils.singleResult(queues);
    }

    @Override
    public void saveQueue(OpenAcdQueue queue) {
        // check if queue name is empty
        if (StringUtils.isBlank(queue.getName())) {
            throw new UserException("&blank.queueName.error");
        }
        // Check for duplicate names before saving the queue
        boolean nameChanged = !queue.isNew() && isNameChanged(queue);
        if (queue.isNew() || nameChanged) {
            checkForDuplicateName(queue);
        }

        // Check if the queue is associated with lines
        if (nameChanged) {
            checkLines(queue);
        }

        if (!queue.isNew()) {
            if (isNameChanged(queue)) {
                // don't rename the queue
                throw new UserException("&msg.err.queueRename");
            }
            getHibernateTemplate().merge(queue);
        } else {
            getHibernateTemplate().save(queue);
        }
    }

    private boolean isNameChanged(OpenAcdQueue queue) {
        String oldName = getQueueById(queue.getId()).getName();
        queue.setOldName(oldName);
        return !oldName.equals(queue.getName());
    }

    private void checkForDuplicateName(OpenAcdQueue queue) {
        String queueName = queue.getName();
        OpenAcdQueue existingQueue = getQueueByName(queueName);
        if (existingQueue != null) {
            throw new UserException("&duplicate.queueName.error", queueName);
        }
    }

    private void checkLines(OpenAcdQueue queue) {
        List<FreeswitchAction> actions = getActionsByData(OpenAcdLine.Q + queue.getOldName());
        if (actions.size() > 0) {
            for (FreeswitchAction action : actions) {
                action.setData(OpenAcdLine.Q + queue.getName());
                getHibernateTemplate().merge(action);
            }
            getHibernateTemplate().flush();
        }
    }

    @Override
    public void deleteQueue(OpenAcdQueue queue) {
        if (queue.getName().equals(DEFAULT_QUEUE) || isUsedByLine(OpenAcdLine.Q + queue.getName())) {
            throw new QueueInUseException();
        } else {
            OpenAcdQueueGroup group = queue.getGroup();
            group.removeQueue(queue);
            getHibernateTemplate().saveOrUpdate(group);
            getHibernateTemplate().delete(queue);
        }
    }

    public final class QueueInUseException extends UserException {
    }

    private boolean isUsedByLine(String data) {
        if (getActionsByData(data).size() > 0) {
            return true;
        }
        return false;
    }

    @Override
    public OpenAcdRecipeStep getRecipeStepById(Integer recipeStepId) {
        return getHibernateTemplate().load(OpenAcdRecipeStep.class, recipeStepId);
    }

    @Override
    public List<OpenAcdReleaseCode> getReleaseCodes() {
        return getHibernateTemplate().loadAll(OpenAcdReleaseCode.class);
    }

    @Override
    public OpenAcdReleaseCode getReleaseCodeById(Integer id) {
        return getHibernateTemplate().load(OpenAcdReleaseCode.class, id);
    }

    @Override
    public void saveReleaseCode(OpenAcdReleaseCode code) {
        // Check for duplicate labels
        if (code.isNew() || (!code.isNew() && isLabelChanged(code))) {
            checkForDuplicateLabel(code);
        }

        if (code.isNew()) {
            getHibernateTemplate().save(code);
        } else {
            getHibernateTemplate().merge(code);
        }
    }

    private boolean isLabelChanged(OpenAcdReleaseCode code) {
        return !getReleaseCodeById(code.getId()).getLabel().equals(code.getLabel());
    }

    private void checkForDuplicateLabel(OpenAcdReleaseCode code) {
        String labelCode = code.getLabel();
        OpenAcdReleaseCode existingCode = getReleaseCodeByLabel(labelCode);
        if (existingCode != null) {
            throw new UserException("&duplicate.codeLabel.error", existingCode);
        }
    }

    private OpenAcdReleaseCode getReleaseCodeByLabel(String label) {
        List<OpenAcdReleaseCode> codes = getHibernateTemplate().findByNamedQueryAndNamedParam(
                OPEN_ACD_RELEASE_CODE_WITH_LABEL, VALUE, label);
        return DataAccessUtils.singleResult(codes);
    }

    @Override
    public void removeReleaseCodes(Collection<Integer> codesId) {
        List<OpenAcdReleaseCode> codes = new ArrayList<OpenAcdReleaseCode>();
        for (Integer id : codesId) {
            OpenAcdReleaseCode code = getReleaseCodeById(id);
            codes.add(code);
        }
        getDaoEventPublisher().publishDeleteCollection(codes);
        getHibernateTemplate().deleteAll(codes);
    }

    public void onSave(Object entity) {
        if (entity instanceof OpenAcdSettings) {
            OpenAcdSettings settings = (OpenAcdSettings) entity;
            m_replicationManager.replicateEntity(new OpenAcdLogConfigCommand(settings.getLogLevel(), settings
                    .getLogDir() + OpenAcdContext.OPENACD_LOG));
        }
    }

    public void onDelete(Object entity) {
        if (entity instanceof User) {
            User user = (User) entity;
            OpenAcdAgent agent = getAgentByUser(user);
            if (agent != null) {
                //must manually de-associate group-agent
                agent.getGroup().removeAgent(agent);
                getDaoEventPublisher().publishDelete(agent);
                //do not call deleteAgent here b/c it will re-insert user into mongo
                //(we don't care about user-group association update since the user is deleted anyway)
                getHibernateTemplate().delete(agent);
            }
        } else if (entity instanceof OpenAcdQueueGroup) {
            getHibernateTemplate().flush();
            OpenAcdQueueGroup qgr = (OpenAcdQueueGroup) entity;
            for (OpenAcdQueue q : qgr.getQueues()) {
                m_replicationManager.removeEntity(q);
            }
        } else if (entity instanceof OpenAcdAgentGroup) {
            OpenAcdAgentGroup aggr = (OpenAcdAgentGroup) entity;
            for (OpenAcdAgent agent : aggr.getAgents()) {
                m_replicationManager.removeEntity(agent);
            }
        } else if (entity instanceof OpenAcdSkillGroup) {
            getHibernateTemplate().flush();
            OpenAcdSkillGroup skillGroup = (OpenAcdSkillGroup) entity;
            for (OpenAcdSkill skill : skillGroup.getSkills()) {
                m_replicationManager.removeEntity(skill);
            }
        }
    }

    public void setAliasManager(AliasManager aliasManager) {
        m_aliasManager = aliasManager;
    }


    @Override
    public void resync() {
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        m_beanFactory = (ListableBeanFactory) beanFactory;
    }

    public void setFeatureManager(FeatureManager featureManager) {
        m_featureManager = featureManager;
    }

    public void setSettingsDao(BeanWithSettingsDao<OpenAcdSettings> settingsDao) {
        m_settingsDao = settingsDao;
    }

    public void setCoreContext(CoreContext coreContext) {
        m_coreContext = coreContext;
    }

    @Override
    public Collection<ProcessDefinition> getProcessDefinitions(SnmpManager manager, Location location) {
        if (!manager.getFeatureManager().isFeatureEnabled(FEATURE, location)) {
            return null;
        }
        ProcessDefinition def = ProcessDefinition.sysvDefault(OPEN_ACD_PROCESS_NAME, "$(sipx.OPENACD_DIR)/bin/"
                + OPEN_ACD_PROCESS_NAME);
        return Collections.singleton(def);
    }

    public void setReplicationManager(ReplicationManager replicationManager) {
        m_replicationManager = replicationManager;
    }

    @Override
    public void getBundleFeatures(FeatureManager featureManager, Bundle b) {
        if (b == Bundle.CALL_CENTER) {
            b.addFeature(FEATURE);
        }
    }

    public void setOpenacdHome(String openAcdHome) {
        m_openacdHome = openAcdHome;
    }

    @Override
    public void featureChangePrecommit(FeatureManager manager, FeatureChangeValidator validator) {
        validator.requiredOnSameHost(FEATURE, FreeswitchFeature.FEATURE);
    }

    @Override
    public void featureChangePostcommit(FeatureManager manager, FeatureChangeRequest request) {
    }
}
