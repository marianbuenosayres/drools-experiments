package org.jbpm.rollback;

import java.util.Collection;
import java.util.List;

import org.drools.core.command.impl.GenericCommand;
import org.drools.core.command.impl.KnowledgeCommandContext;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.definition.process.Connection;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.command.Context;

public class RetriggerNodesCommand implements GenericCommand<Void> {

	private static final long serialVersionUID = 3667108601651889505L;
	
	private final Long processInstanceId;
	
	public RetriggerNodesCommand(Long processInstanceId) {
		this.processInstanceId = processInstanceId;
	}

	//get all active node instances and re-trigger any active
	//state node you can find inside the transaction
	public Void execute(Context context) {
		KieSession ksession = ((KnowledgeCommandContext) context).getKieSession();
		ProcessSnapshotLogger snapshotLogger = ProcessSnapshotLogger.find(ksession);
		if (snapshotLogger != null) {
			ksession.removeEventListener(snapshotLogger);
		}
		ProcessInstance instance = ksession.getProcessInstance(processInstanceId);
		WorkflowProcessInstanceImpl instanceImpl = (WorkflowProcessInstanceImpl) instance;
		Collection<NodeInstance> nodeInstances = instanceImpl.getNodeInstances(true);
		List<String> completedNodes = instanceImpl.getCompletedNodeIds();
		for (NodeInstance nodeInstance : nodeInstances) {
			List<Connection> froms = nodeInstance.getNode().getIncomingConnections(Node.CONNECTION_DEFAULT_TYPE);
			for (Connection from : froms) {
				Object id = from.getFrom().getMetaData().get("UniqueId");
				long flowId = from.getFrom().getId();
				if (completedNodes.contains(id)) {
					nodeInstance.trigger(new MockNodeInstance(flowId), Node.CONNECTION_DEFAULT_TYPE);
				}
			}
		}
		if (snapshotLogger != null) {
			ksession.addEventListener(snapshotLogger);
		}
		return null;
	}
	
	private static class MockNodeInstance extends NodeInstanceImpl {
		private static final long serialVersionUID = 1L;

		private final long id;
		
		public MockNodeInstance(long id) {
			this.id = id;
		}
		
		@Override
		public void internalTrigger(org.kie.api.runtime.process.NodeInstance from, String type) { }
		
		@Override
		public long getNodeId() {
			return this.id;
		}
	}
}
