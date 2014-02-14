package org.jbpm.rollback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.transaction.UserTransaction;

import org.drools.core.command.CommandService;
import org.drools.core.command.impl.AbstractInterceptor;
import org.drools.core.command.impl.CommandBasedStatefulKnowledgeSession;
import org.drools.core.command.runtime.DisposeCommand;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalRuleBase;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.impl.StatefulKnowledgeSessionImpl;
import org.drools.core.marshalling.impl.MarshallerWriteContext;
import org.drools.core.marshalling.impl.PersisterHelper;
import org.drools.persistence.SingleSessionCommandService;
import org.jbpm.marshalling.impl.JBPMMessages;
import org.jbpm.marshalling.impl.ProcessInstanceMarshaller;
import org.jbpm.marshalling.impl.ProcessMarshallerRegistry;
import org.jbpm.marshalling.impl.ProtobufRuleFlowProcessInstanceMarshaller;
import org.jbpm.persistence.processinstance.ProcessInstanceInfo;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.rollback.acceptor.ProcessSnapshotAcceptor;
import org.jbpm.rollback.util.PersistenceUtil;
import org.kie.api.command.Command;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;

public class ProcessSnapshotLogger extends AbstractInterceptor implements ProcessEventListener {
	
	private final KieSession ksession;
	private final PersistenceUtil persistence;
	private Set<String> toTakeSnapshot = new HashSet<String>();
	
	public static ProcessSnapshotLogger find(KieSession session) {
		Collection<ProcessEventListener> listeners = session.getProcessEventListeners();
		ProcessSnapshotLogger retval = null;
		for (ProcessEventListener listener : listeners) {
			if (ProcessEventListener.class.isAssignableFrom(listener.getClass())) {
				retval = (ProcessSnapshotLogger) listener;
				break;
			}
		}
		return retval;
	}
	
	public ProcessSnapshotLogger(KieSession ksession) {
		ksession.addEventListener(this);
		this.ksession = ksession;
		Boolean bool = (Boolean) ksession.getEnvironment().get("IS_JTA_TRANSACTION");
		persistence = new PersistenceUtil(bool == null || bool.booleanValue());
		registerInterceptor();
	}
	
	private void registerInterceptor() throws ProcessRollbackException {
		if (this.ksession instanceof CommandBasedStatefulKnowledgeSession) {
			CommandBasedStatefulKnowledgeSession cmdSession = (CommandBasedStatefulKnowledgeSession) this.ksession;
			CommandService cmdService = cmdSession.getCommandService();
			if (cmdService instanceof SingleSessionCommandService) {
				SingleSessionCommandService ssCmdService = (SingleSessionCommandService) cmdService;
				ssCmdService.addInterceptor(this);
			} else {
				throw new ProcessRollbackException("ProcessSnapshotLogger needs a SingleSessionCommandService " +
						"inside the CommandBasedStatefulKnowledgeSession to add itself as an interceptor");
			}
		} else {
			throw new ProcessRollbackException("ProcessSnapshotLogger needs a CommandBasedStatefulKnowledgeSession " +
				"to add itself as an interceptor to its SingleSessionCommandService");
		}
	}
	
	public void afterNodeLeft(ProcessNodeLeftEvent event) {
		// mark for saving new snapshot
		String processId = event.getProcessInstance().getProcessId();
		Long processInstanceId = event.getProcessInstance().getId();
		markForNewSnapshot(processId, processInstanceId);
	}
	
	public void afterNodeTriggered(ProcessNodeTriggeredEvent event) { 
		// mark for saving new snapshot
		String processId = event.getProcessInstance().getProcessId();
		Long processInstanceId = event.getProcessInstance().getId();
		markForNewSnapshot(processId, processInstanceId);
	}
	
	public void afterProcessCompleted(ProcessCompletedEvent event) {
		// Delete related snapshots
		String processId = event.getProcessInstance().getProcessId();
		Long processInstanceId = event.getProcessInstance().getId();
		deleteSnapshots(processId, processInstanceId);
	}
	
	public void afterProcessStarted(ProcessStartedEvent event) {
		// mark for saving new snapshot
		String processId = event.getProcessInstance().getProcessId();
		Long processInstanceId = event.getProcessInstance().getId();
		markForNewSnapshot(processId, processInstanceId);
	}
	
	public void afterVariableChanged(ProcessVariableChangedEvent event) {
		// mark for saving new snapshot
		String processId = event.getProcessInstance().getProcessId();
		Long processInstanceId = event.getProcessInstance().getId();
		markForNewSnapshot(processId, processInstanceId);
	}
	
	public void beforeNodeLeft(ProcessNodeLeftEvent event) { }
	public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) { }
	public void beforeProcessCompleted(ProcessCompletedEvent event) { }
	public void beforeProcessStarted(ProcessStartedEvent event) { }
	public void beforeVariableChanged(ProcessVariableChangedEvent event) { }
	
	public <T> T execute(Command<T> command) {
		T result = executeNext(command);
		if (validCommand(command)) {
			KieRuntime kruntime = ProcessRollback.extractKnowledgeRuntime(this.ksession);
			for (String key : toTakeSnapshot) {
				int breakpoint = key.lastIndexOf(".");
				String processId = key.substring(0, breakpoint);
				Long processInstanceId = Long.valueOf(key.substring(breakpoint + 1));
				if (shouldTakeSnapshots(processId, processInstanceId)) {
					ProcessInstance instance = findProcessInstance(kruntime, processInstanceId);
					if (instance != null) {
						saveNewSnapshot(instance, kruntime);
					}
				}
			}
		}
		toTakeSnapshot.clear();
		return result;
	}
	
	protected boolean validCommand(Command<?> command){
		return !(command instanceof DisposeCommand) && !(command instanceof RetriggerNodesCommand);
	}

	protected ProcessInstance findProcessInstance(KieRuntime kruntime, Long processInstanceId) {
		EntityManagerFactory emf = (EntityManagerFactory) kruntime.getEnvironment().get(EnvironmentName.ENTITY_MANAGER_FACTORY);
		EntityManager em = emf.createEntityManager();
		ProcessInstanceInfo info = em.find(ProcessInstanceInfo.class, processInstanceId);
		if (info != null) {
			ProcessInstance instance = info.getProcessInstance((InternalKnowledgeRuntime) kruntime, kruntime.getEnvironment(), true);
			org.kie.api.definition.process.Process proc = kruntime.getKieBase().getProcess(instance.getProcessId());
			if (proc == null) {
				throw new IllegalArgumentException( "Could not find process " + instance.getProcessId() );
			}
			((ProcessInstanceImpl) instance).setProcess(proc);
			return instance;
		} 
		return null;
	}
	
	protected void markForNewSnapshot(String processId, Long processInstanceId) {
		String key = processId + "." + processInstanceId;
		toTakeSnapshot.add(key);
	}
	
	protected void deleteSnapshots(String processId, Long processInstanceId) {
		toTakeSnapshot.remove(processId + "." + processInstanceId);
		EntityManager em = getEntityManager();
		Query query = em.createQuery("delete from " + ProcessSnapshot.class.getName() + " ps" +
				" where ps.processId = :procDefId and ps.processInstanceId = :procInstId");
		query.setParameter("procDefId", processId).setParameter("procInstId", processInstanceId);
		query.executeUpdate();
	}
	
	protected void saveNewSnapshot(ProcessInstance instance, KieRuntime kruntime) {
		byte[] binarySnapshot = writeProcessInstanceFully(instance, kruntime);
		Long processInstanceId = instance.getId();
		String processId = instance.getProcessId();
		ProcessSnapshot snapshot = new ProcessSnapshot();
		snapshot.setBinarySnapshot(binarySnapshot);
		snapshot.setProcessId(processId);
		snapshot.setProcessInstanceId(processInstanceId);
		
		EntityManager em = getEntityManager();
		UserTransaction ut = persistence.joinTransaction(em);
		em.persist(snapshot);
		persistence.commitTransaction(em, ut);
	}
	
	protected ProcessInstanceInfo findProcessInstanceInfo(Long processInstanceId) {
		EntityManager em = getEntityManager();
		ProcessInstanceInfo pinfo = em.find(ProcessInstanceInfo.class, processInstanceId);
		return pinfo;
	}

	protected boolean shouldTakeSnapshots(String processId, long processInstanceId) {
		Environment environment = ProcessRollback.extractKnowledgeRuntime(this.ksession).getEnvironment();
		ProcessSnapshotAcceptor[] acceptors = (ProcessSnapshotAcceptor[]) 
				environment.get(ProcessRollback.ROLLBACK_SELECTION_STRATEGY);
		boolean retval = false;
		if (acceptors == null || acceptors.length == 0) {
			retval = false;
		} else {
			for (ProcessSnapshotAcceptor acceptor : acceptors) {
				if (acceptor.accept(processId, processInstanceId)) {
					retval = true;
					break;
				}
			}
		}
		return retval;
	}

	protected EntityManager getEntityManager() {
		return ProcessRollback.getEntityManager(
				ProcessRollback.extractKnowledgeRuntime(this.ksession).getEnvironment());
	}
	
	protected byte[] writeProcessInstanceFully(ProcessInstance instance, KieRuntime kruntime) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			MarshallerWriteContext context = new MarshallerWriteContext( baos, 
																		 (InternalRuleBase) ((InternalKnowledgeBase) kruntime.getKieBase()).getRuleBase(),
																		 ((StatefulKnowledgeSessionImpl) kruntime).getInternalWorkingMemory(),
																		 null,
																		 null, 
																		 kruntime.getEnvironment());
			String processInstanceType = instance.getProcess().getType();
			ObjectOutputStream stream = context.stream;
			stream.writeUTF( processInstanceType );
			ProcessInstanceMarshaller marshaller = ProcessMarshallerRegistry.INSTANCE.getMarshaller( processInstanceType );
			Object result = marshaller.writeProcessInstance(context, new ProcessInstanceOverrider(instance));
            if( marshaller instanceof ProtobufRuleFlowProcessInstanceMarshaller && result != null ) {
                JBPMMessages.ProcessInstance _instance = (JBPMMessages.ProcessInstance) result;
                PersisterHelper.writeToStreamWithHeader( context, _instance );
            }

			context.close();
			
			return baos.toByteArray();
		} catch ( IOException e ) {
            e.printStackTrace();
            throw new IllegalArgumentException( "IOException while writing process instance: " + e.getMessage(), e );
		}
	}
}
