package org.jbpm.rollback;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;

import org.drools.core.command.CommandService;
import org.drools.core.command.impl.CommandBasedStatefulKnowledgeSession;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalRuleBase;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.impl.StatefulKnowledgeSessionImpl;
import org.drools.core.marshalling.impl.MarshallerReaderContext;
import org.drools.core.marshalling.impl.ProtobufMarshaller;
import org.drools.persistence.SingleSessionCommandService;
import org.jbpm.marshalling.impl.ProcessInstanceMarshaller;
import org.jbpm.marshalling.impl.ProcessMarshallerRegistry;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.ProcessInstanceManager;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;

public class ProcessRollback {

	public static final String ROLLBACK_SELECTION_STRATEGY = "org.kie.api.persistence.RollbackSellectionStrategy";
	
	static EntityManager getEntityManager(Environment environment) {
		EntityManagerFactory emf = (EntityManagerFactory) 
			environment.get(EnvironmentName.ENTITY_MANAGER_FACTORY);
		EntityManager em = emf.createEntityManager();
		return em;
	}

	public static void goBack(KieSession ksession, Long processInstanceId) {
		goBack(ksession, processInstanceId, 1);
	}
	
	public static void goBack(KieSession ksession, Long processInstanceId, int numberOfSteps) {
		InternalKnowledgeRuntime kruntime = extractKnowledgeRuntime(ksession);
		ProcessInstance instance = ksession.getProcessInstance(processInstanceId);
		WorkflowProcessInstanceImpl instanceImpl = (WorkflowProcessInstanceImpl) instance;
		if (instanceImpl == null) {
			throw new ProcessRollbackException("Process no longer in runtime");
		}
		instanceImpl.setKnowledgeRuntime(kruntime);
		instanceImpl.disconnect();
		ProcessSnapshot snapshot = loadLastSnapshot(kruntime, processInstanceId, numberOfSteps);
		if (snapshot == null) {
			throw new ProcessRollbackException("No previous state found");
		}
		WorkflowProcessInstanceImpl instanceImplOld = getProcessInstance(snapshot.getBinarySnapshot(), kruntime);
		if (instanceImplOld.getKnowledgeRuntime() == null) {
			instanceImplOld.setKnowledgeRuntime(kruntime);
		}
		//clear old memory reservoirs for that particular process instance
		ProcessInstanceManager piManager = ((InternalProcessRuntime) kruntime.getProcessRuntime()).getProcessInstanceManager();
		piManager.internalRemoveProcessInstance(piManager.getProcessInstance(instanceImplOld.getId()));
		CommandService cmdService = extractCommandService(ksession);
		//rewrite in database
		cmdService.execute(new RewriteProcessInfoCommand(instanceImplOld));
		//reconnect the desired stage
		instanceImplOld.reconnect();
		
		//re-trigger any active state node you can find inside a transaction
		cmdService.execute(new RetriggerNodesCommand(instanceImplOld.getId()));
		
	}

	static InternalKnowledgeRuntime extractKnowledgeRuntime(KieSession ksession) {
		if (ksession instanceof InternalKnowledgeRuntime) {
			return (InternalKnowledgeRuntime) ksession;
		} else if (ksession instanceof CommandBasedStatefulKnowledgeSession) {
			CommandService commandService = ((CommandBasedStatefulKnowledgeSession) ksession).getCommandService();
			KieSession actualKieSession = ((SingleSessionCommandService) commandService).getKieSession();
			return (InternalKnowledgeRuntime) actualKieSession;
		} else {
			throw new IllegalArgumentException(
					"Don't know how to extract the InternalKnowledgeRuntime from class " +
					ksession.getClass().getName());
		}
	}
	
	static CommandService extractCommandService(KieSession ksession) {
		if (ksession instanceof CommandBasedStatefulKnowledgeSession) {
			return ((CommandBasedStatefulKnowledgeSession) ksession).getCommandService();
		}
		throw new IllegalArgumentException(
				"Can't extract a command session from class " + ksession.getClass().getName());
	}
	
	protected static ProcessSnapshot loadLastSnapshot(KieRuntime kruntime, Long processInstanceId, int position) {
		EntityManager em = getEntityManager(kruntime.getEnvironment());
		String ql = "select ps from " + ProcessSnapshot.class.getName() + " ps where ps.processInstanceId = :pId order by ps.id desc";
		TypedQuery<ProcessSnapshot> q = em.createQuery(ql, ProcessSnapshot.class);
		q.setParameter("pId", processInstanceId);
		q.setFirstResult(position);
		List<ProcessSnapshot> list = q.getResultList();
		return list.size() > 0 ? list.iterator().next() : null; 
	}

	private static WorkflowProcessInstanceImpl getProcessInstance(byte[] processInstanceByteArray, KieRuntime kruntime) {
		WorkflowProcessInstanceImpl processInstance = null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream( processInstanceByteArray );
            MarshallerReaderContext context = new MarshallerReaderContext( bais,
                                                                           (InternalRuleBase) ((InternalKnowledgeBase) kruntime.getKieBase()).getRuleBase(),
                                                                           null,
                                                                           null,
                                                                           ProtobufMarshaller.TIMER_READERS,
                                                                           kruntime.getEnvironment());
            ObjectInputStream stream = context.stream;
            String processInstanceType = stream.readUTF();
            ProcessInstanceMarshaller marshaller = ProcessMarshallerRegistry.INSTANCE.getMarshaller( processInstanceType );
        	context.wm = ((StatefulKnowledgeSessionImpl) kruntime).getInternalWorkingMemory();
            processInstance = (WorkflowProcessInstanceImpl) marshaller.readProcessInstance(context);
            context.close();
        } catch ( IOException e ) {
            e.printStackTrace();
            throw new IllegalArgumentException( "IOException while loading process instance: " + e.getMessage(), e );
        }
        return processInstance;
	}
}
