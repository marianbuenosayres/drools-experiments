package org.jbpm.rollback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.drools.core.common.InternalKnowledgeRuntime;
import org.jbpm.process.core.Context;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.instance.ContextInstance;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.instance.NodeInstance;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.NodeContainer;
import org.kie.api.definition.process.Process;
import org.kie.api.definition.process.WorkflowProcess;
import org.kie.api.runtime.process.EventListener;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.rule.Agenda;

/**
 * Needed to make sure that getActiveNodes() call in process instance marshaller 
 * can be used recursively. After that becomes configurable through the context,
 * this class and its usage are no longer needed.
 * 
 * @author marianbuenosayres
 *
 */
public class ProcessInstanceOverrider extends RuleFlowProcessInstance {

	private static final long serialVersionUID = -6004l;
	private final RuleFlowProcessInstance wrapper;
	
	public ProcessInstanceOverrider(ProcessInstance processInstance) {
		this.wrapper = (RuleFlowProcessInstance) processInstance;
	}

	@Override
	public RuleFlowProcess getRuleFlowProcess() {
		return wrapper.getRuleFlowProcess();
	}

	@Override
	public void internalStart() {
		wrapper.internalStart();
	}

	@Override
	public int hashCode() {
		return wrapper.hashCode();
	}

	@Override
	public void setId(long id) {
		wrapper.setId(id);
	}

	@Override
	public long getId() {
		return wrapper.getId();
	}

	@Override
	public void setProcess(Process process) {
		wrapper.setProcess(process);
	}

	@Override
	public void updateProcess(Process process) {
		wrapper.updateProcess(process);
	}

	@Override
	public String getProcessXml() {
		return wrapper.getProcessXml();
	}

	@Override
	public void setProcessXml(String processXml) {
		wrapper.setProcessXml(processXml);
	}

	@Override
	public Process getProcess() {
		return wrapper.getProcess();
	}

	@Override
	public NodeContainer getNodeContainer() {
		return wrapper.getNodeContainer();
	}

	@Override
	public void setProcessId(String processId) {
		wrapper.setProcessId(processId);
	}

	@Override
	public void addNodeInstance(NodeInstance nodeInstance) {
		wrapper.addNodeInstance(nodeInstance);
	}

	@Override
	public String getProcessId() {
		return wrapper.getProcessId();
	}

	@Override
	public String getProcessName() {
		return wrapper.getProcessName();
	}

	@Override
	public void removeNodeInstance(NodeInstance nodeInstance) {
		wrapper.removeNodeInstance(nodeInstance);
	}

	@Override
	public boolean equals(Object obj) {
		return wrapper.equals(obj);
	}

	@Override
	public void internalSetState(int state) {
		wrapper.internalSetState(state);
	}

	@Override
	public Collection<org.kie.api.runtime.process.NodeInstance> getNodeInstances() {
		return new ArrayList<org.kie.api.runtime.process.NodeInstance>(wrapper.getNodeInstances(true));
	}

	@Override
	public int getState() {
		return wrapper.getState();
	}

	@Override
	public void setKnowledgeRuntime(InternalKnowledgeRuntime kruntime) {
		wrapper.setKnowledgeRuntime(kruntime);
	}

	@Override
	public Collection<NodeInstance> getNodeInstances(boolean recursive) {
		return wrapper.getNodeInstances(recursive);
	}

	@Override
	public InternalKnowledgeRuntime getKnowledgeRuntime() {
		return wrapper.getKnowledgeRuntime();
	}

	@Override
	public Agenda getAgenda() {
		return wrapper.getAgenda();
	}

	@Override
	public ContextContainer getContextContainer() {
		return wrapper.getContextContainer();
	}

	@Override
	public void setContextInstance(String contextId, ContextInstance contextInstance) {
		wrapper.setContextInstance(contextId, contextInstance);
	}

	@Override
	public NodeInstance getNodeInstance(long nodeInstanceId) {
		return wrapper.getNodeInstance(nodeInstanceId);
	}

	@Override
	public ContextInstance getContextInstance(String contextId) {
		return wrapper.getContextInstance(contextId);
	}

	@Override
	public List<String> getActiveNodeIds() {
		return wrapper.getActiveNodeIds();
	}

	@Override
	public List<ContextInstance> getContextInstances(String contextId) {
		return wrapper.getContextInstances(contextId);
	}

	@Override
	public void addContextInstance(String contextId, ContextInstance contextInstance) {
		wrapper.addContextInstance(contextId, contextInstance);
	}

	@Override
	public NodeInstance getFirstNodeInstance(long nodeId) {
		return wrapper.getFirstNodeInstance(nodeId);
	}

	@Override
	public List<NodeInstance> getNodeInstances(long nodeId) {
		return wrapper.getNodeInstances(nodeId);
	}

	@Override
	public void removeContextInstance(String contextId, ContextInstance contextInstance) {
		wrapper.removeContextInstance(contextId, contextInstance);
	}

	@Override
	public ContextInstance getContextInstance(String contextId, long id) {
		return wrapper.getContextInstance(contextId, id);
	}

	@Override
	public NodeInstance getNodeInstance(Node node) {
		return wrapper.getNodeInstance(node);
	}

	@Override
	public ContextInstance getContextInstance(Context context) {
		return wrapper.getContextInstance(context);
	}

	@Override
	public long getNodeInstanceCounter() {
		return wrapper.getNodeInstanceCounter();
	}

	@Override
	public void internalSetNodeInstanceCounter(long nodeInstanceCounter) {
		wrapper.internalSetNodeInstanceCounter(nodeInstanceCounter);
	}

	@Override
	public WorkflowProcess getWorkflowProcess() {
		return wrapper.getWorkflowProcess();
	}

	@Override
	public Object getVariable(String name) {
		return wrapper.getVariable(name);
	}

	@Override
	public Map<String, Object> getVariables() {
		return wrapper.getVariables();
	}

	@Override
	public Map<String, Object> getMetaData() {
		return wrapper.getMetaData();
	}

	@Override
	public void setMetaData(String name, Object data) {
		wrapper.setMetaData(name, data);
	}

	@Override
	public void setOutcome(String outcome) {
		wrapper.setOutcome(outcome);
	}

	@Override
	public String getOutcome() {
		return wrapper.getOutcome();
	}

	@Override
	public long getParentProcessInstanceId() {
		return wrapper.getParentProcessInstanceId();
	}

	@Override
	public void setParentProcessInstanceId(long parentProcessInstanceId) {
		wrapper.setParentProcessInstanceId(parentProcessInstanceId);
	}

	@Override
	public void setVariable(String name, Object value) {
		wrapper.setVariable(name, value);
	}

	@Override
	public void setState(int state, String outcome) {
		wrapper.setState(state, outcome);
	}

	@Override
	public void setState(int state) {
		wrapper.setState(state);
	}

	@Override
	public void disconnect() {
		wrapper.disconnect();
	}

	@Override
	public void reconnect() {
		wrapper.reconnect();
	}

	@Override
	public String toString() {
		return wrapper.toString();
	}

	@Override
	public void start() {
		wrapper.start();
	}

	@Override
	public void signalEvent(String type, Object event) {
		wrapper.signalEvent(type, event);
	}

	@Override
	public void addEventListener(String type, EventListener listener, boolean external) {
		wrapper.addEventListener(type, listener, external);
	}

	@Override
	public void removeEventListener(String type, EventListener listener, boolean external) {
		wrapper.removeEventListener(type, listener, external);
	}

	@Override
	public String[] getEventTypes() {
		return wrapper.getEventTypes();
	}

	@Override
	public void nodeInstanceCompleted(NodeInstance nodeInstance, String outType) {
		wrapper.nodeInstanceCompleted(nodeInstance, outType);
	}

	public int getLevelForNode(String uniqueID) {
		return wrapper.getLevelForNode(uniqueID);
	}

	public void addCompletedNodeId(String uniqueId) {
		wrapper.addCompletedNodeId(uniqueId);
	}

	public List<String> getCompletedNodeIds() {
		return wrapper.getCompletedNodeIds();
	}

	public int getCurrentLevel() {
		return wrapper.getCurrentLevel();
	}

	public void setCurrentLevel(int currentLevel) {
		wrapper.setCurrentLevel(currentLevel);
	}

	public Map<String, Integer> getIterationLevels() {
		return wrapper.getIterationLevels();
	}
}
