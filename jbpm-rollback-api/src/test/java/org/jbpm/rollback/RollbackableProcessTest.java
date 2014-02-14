package org.jbpm.rollback;

import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;

import javax.persistence.Persistence;

import org.drools.core.impl.EnvironmentFactory;
import org.drools.core.io.impl.ClassPathResource;
import org.jbpm.rollback.acceptor.ProcessDefinitionSnapshotAcceptor;
import org.jbpm.rollback.acceptor.ProcessSnapshotAcceptor;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.JoinInstance;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message.Level;
import org.kie.api.persistence.jpa.KieStoreServices;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.internal.KnowledgeBaseFactory;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

public class RollbackableProcessTest {

	private PoolingDataSource ds = new PoolingDataSource();
	
	@Before
	public void setUp() throws Exception {
		
		 ds.setUniqueName("jdbc/testDS1");
	     //NON XA CONFIGS
	     ds.setClassName("org.h2.jdbcx.JdbcDataSource");
	     ds.setMaxPoolSize(3);
	     ds.setAllowLocalTransactions(true);
	     ds.getDriverProperties().put("user", "sa");
	     ds.getDriverProperties().put("password", "sasa");
	     ds.getDriverProperties().put("URL", "jdbc:h2:mem:mydb");
	     ds.init();
	}
	
	@After
	public void tearDown() throws Exception {
		TransactionManagerServices.getTransactionManager().shutdown();
		ds.close();
		System.gc();
	}
	
	@Test
	public void testRollbackToWorkItems() throws Exception {
		KieServices ks = KieServices.Factory.get();
		KieBase kbase = generateKieBase(ks, "processes/workitem-process.bpmn2");
		Environment env = createEnvironment();
		env.set(ProcessRollback.ROLLBACK_SELECTION_STRATEGY, new ProcessSnapshotAcceptor[] {
				new ProcessDefinitionSnapshotAcceptor("workitem-process")
		});
		Properties ksprops = new Properties();
		KieSessionConfiguration ksconf = KnowledgeBaseFactory.newKnowledgeSessionConfiguration(ksprops);
		KieStoreServices kstore = ks.getStoreServices();
		KieSession ksession = kstore.newKieSession(kbase, ksconf, env);
		
		TestAsyncWorkItemHandler handler1 = new TestAsyncWorkItemHandler();
		TestAsyncWorkItemHandler handler2 = new TestAsyncWorkItemHandler();
		TestAsyncWorkItemHandler handler3 = new TestAsyncWorkItemHandler();
		
		ksession.getWorkItemManager().registerWorkItemHandler("task1", handler1);
		ksession.getWorkItemManager().registerWorkItemHandler("task2", handler2);
		ksession.getWorkItemManager().registerWorkItemHandler("task3", handler3);

		new ProcessSnapshotLogger(ksession);

		ProcessInstance instance = ksession.createProcessInstance("workitem-process", new HashMap<String, Object>());
		ksession.startProcessInstance(instance.getId());
		
		ksession.getWorkItemManager().completeWorkItem(handler1.getWorkItem().getId(), null);
		ksession.getWorkItemManager().completeWorkItem(handler2.getWorkItem().getId(), null);

		ProcessRollback.goBack(ksession, instance.getId(), 2);
		
		Assert.assertNotNull(handler1.getWorkItem());
		
		Assert.assertEquals(2, handler1.getActivations());
		
	}

	@Test
	public void testRollbackToTimerAndGateway() throws Exception {
		KieServices ks = KieServices.Factory.get();
		KieBase kbase = generateKieBase(ks, "processes/timer-process.bpmn2");
		Properties ksprops = new Properties();
		KieSessionConfiguration ksconf = KnowledgeBaseFactory.newKnowledgeSessionConfiguration(ksprops);
		Environment env = createEnvironment();
		env.set(ProcessRollback.ROLLBACK_SELECTION_STRATEGY, new ProcessSnapshotAcceptor[] {
				new ProcessDefinitionSnapshotAcceptor("timer-process")
		});
		KieStoreServices kstore = ks.getStoreServices();
		KieSession ksession = kstore.newKieSession(kbase, ksconf, env);
		int sessionId = ksession.getId();
		
		TestAsyncWorkItemHandler handler1 = new TestAsyncWorkItemHandler();
		TestAsyncWorkItemHandler handler2 = new TestAsyncWorkItemHandler();
		
		ksession.getWorkItemManager().registerWorkItemHandler("task1", handler1);
		ksession.getWorkItemManager().registerWorkItemHandler("task2", handler2);
		new ProcessSnapshotLogger(ksession);

		ProcessInstance instance = ksession.createProcessInstance("timer-process", new HashMap<String, Object>());
		ksession.startProcessInstance(instance.getId());

		WorkItem workItem1 = handler1.getWorkItem();
		Assert.assertNotNull(workItem1);
		Assert.assertNull(handler1.getWorkItem());
		//first safe state: task1 completed
		ksession.getWorkItemManager().completeWorkItem(workItem1.getId(), null);
		ksession.dispose();
		
		ksession = kstore.loadKieSession(sessionId, kbase, ksconf, env);
		ksession.getWorkItemManager().registerWorkItemHandler("task1", handler1);
		ksession.getWorkItemManager().registerWorkItemHandler("task2", handler2);
		new ProcessSnapshotLogger(ksession);
		//second safe state: timer completed, waiting on task2
		Thread.sleep(5000);

		WorkItem workItem2 = handler2.getWorkItem();
		Assert.assertNotNull(workItem2);
		Assert.assertNull(handler2.getWorkItem());
		
		ProcessRollback.goBack(ksession, instance.getId());
		
		WorkItem rollbackWorkItem2 = handler2.getWorkItem();
		Assert.assertNull(rollbackWorkItem2);
		
		WorkflowProcessInstanceImpl rollbackedInstance = (WorkflowProcessInstanceImpl) ksession.getProcessInstance(instance.getId());
		Collection<NodeInstance> nodeInstances = rollbackedInstance.getNodeInstances(true);

		Assert.assertEquals(2, nodeInstances.size());
	}

	@Test
	public void testRollbackToSignalAndGateway() throws Exception {
		KieServices ks = KieServices.Factory.get();
		KieBase kbase = generateKieBase(ks, "processes/signal-process.bpmn2");
		Properties ksprops = new Properties();
		KieSessionConfiguration ksconf = KnowledgeBaseFactory.newKnowledgeSessionConfiguration(ksprops);
		Environment env = createEnvironment();
		env.set(ProcessRollback.ROLLBACK_SELECTION_STRATEGY, new ProcessSnapshotAcceptor[] {
				new ProcessDefinitionSnapshotAcceptor("signal-process")
		});
		KieStoreServices kstore = ks.getStoreServices();
		KieSession ksession = kstore.newKieSession(kbase, ksconf, env);
		int sessionId = ksession.getId();
		
		TestAsyncWorkItemHandler handler1 = new TestAsyncWorkItemHandler();
		TestAsyncWorkItemHandler handler2 = new TestAsyncWorkItemHandler();
		
		ksession.getWorkItemManager().registerWorkItemHandler("task1", handler1);
		ksession.getWorkItemManager().registerWorkItemHandler("task2", handler2);

		new ProcessSnapshotLogger(ksession);

		ProcessInstance instance = ksession.createProcessInstance("signal-process", new HashMap<String, Object>());
		//first safe state: task1 pending
		ksession.startProcessInstance(instance.getId());
		
		WorkItem workItem1 = handler1.getWorkItem();
		Assert.assertNotNull(workItem1);
		Assert.assertNull(handler1.getWorkItem());
		//second safe state: task1 completed, waiting on join
		ksession.getWorkItemManager().completeWorkItem(workItem1.getId(), null);
		ksession.dispose();
		
		ksession = kstore.loadKieSession(sessionId, kbase, ksconf, env);
		ksession.getWorkItemManager().registerWorkItemHandler("task1", handler1);
		ksession.getWorkItemManager().registerWorkItemHandler("task2", handler2);

		new ProcessSnapshotLogger(ksession);

		//third safe state: signal sent, waiting for task2
		System.out.println(">>>>Before signalEvent");
		ksession.signalEvent("externalSignal", new Object(), instance.getId());
		System.out.println(">>>>After signalEvent");

		WorkItem workItem2 = handler2.getWorkItem();
		Assert.assertNotNull(workItem2);
		Assert.assertNull(handler2.getWorkItem());
		
		ProcessRollback.goBack(ksession, instance.getId());
		
		WorkflowProcessInstanceImpl rollbackedInstance = (WorkflowProcessInstanceImpl) ksession.getProcessInstance(instance.getId());
		Collection<NodeInstance> nodeInstances = rollbackedInstance.getNodeInstances(true);
		//before signal was sent we were waiting on join
		Assert.assertEquals(1, nodeInstances.size());
		WorkItem rollbackWorkItem1 = handler1.getWorkItem();
		WorkItem rollbackWorkItem2 = handler2.getWorkItem();
		Assert.assertNull(rollbackWorkItem1);
		Assert.assertNull(rollbackWorkItem2);
		NodeInstance rollbackToNode = nodeInstances.iterator().next();
		Assert.assertTrue(JoinInstance.class.isAssignableFrom(rollbackToNode.getClass()));
	}
	
	@Test(expected=ProcessRollbackException.class)
	public void testRollbackExceptionWhenCompletedProcess() throws Exception {
		KieServices ks = KieServices.Factory.get();
		KieBase kbase = generateKieBase(ks, "processes/workitem-process.bpmn2");
		Environment env = createEnvironment();
		env.set(ProcessRollback.ROLLBACK_SELECTION_STRATEGY, new ProcessSnapshotAcceptor[] {
				new ProcessDefinitionSnapshotAcceptor("workitem-process")
		});
		Properties ksprops = new Properties();
		KieSessionConfiguration ksconf = KnowledgeBaseFactory.newKnowledgeSessionConfiguration(ksprops);
		KieStoreServices kstore = ks.getStoreServices();
		KieSession ksession = kstore.newKieSession(kbase, ksconf, env);
		
		TestAsyncWorkItemHandler handler1 = new TestAsyncWorkItemHandler();
		TestAsyncWorkItemHandler handler2 = new TestAsyncWorkItemHandler();
		TestAsyncWorkItemHandler handler3 = new TestAsyncWorkItemHandler();
		
		ksession.getWorkItemManager().registerWorkItemHandler("task1", handler1);
		ksession.getWorkItemManager().registerWorkItemHandler("task2", handler2);
		ksession.getWorkItemManager().registerWorkItemHandler("task3", handler3);

		new ProcessSnapshotLogger(ksession);

		ProcessInstance instance = ksession.createProcessInstance("workitem-process", new HashMap<String, Object>());
		ksession.startProcessInstance(instance.getId());
		
		ksession.getWorkItemManager().completeWorkItem(handler1.getWorkItem().getId(), null);
		ksession.getWorkItemManager().completeWorkItem(handler2.getWorkItem().getId(), null);
		ksession.getWorkItemManager().completeWorkItem(handler3.getWorkItem().getId(), null);
		
		Assert.assertNull(ksession.getProcessInstance(instance.getId()));

		ProcessRollback.goBack(ksession, instance.getId());
	}
	
	private Environment createEnvironment() {
		Environment env = EnvironmentFactory.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, Persistence.createEntityManagerFactory("org.jbpm.persistence.jpa"));
		env.set(EnvironmentName.TRANSACTION_MANAGER, TransactionManagerServices.getTransactionManager());
		return env;
	}

	private KieBase generateKieBase(KieServices ks, String processFile) {
		KieRepository kr = ks.getRepository();
		ks.getKieClasspathContainer();
		KieFileSystem kfs = ks.newKieFileSystem();
		kfs.write(new ClassPathResource(processFile));
		KieBuilder kbuilder = ks.newKieBuilder(kfs);
		kbuilder.buildAll();
		if (kbuilder.getResults().hasMessages(Level.ERROR)) {
			throw new IllegalStateException("Problem reading bpmn2: " + kbuilder.getResults().toString());
		}
		KieContainer kcont = ks.newKieContainer(kr.getDefaultReleaseId());
		return kcont.getKieBase();
	}

	private static class TestAsyncWorkItemHandler implements WorkItemHandler {

		private WorkItem workItem;
		private int activations = 0;
		
		public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
			System.out.println("Starting call to handler " + workItem.getName());
			this.workItem = workItem;
			this.activations++;
		}

		public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
			this.workItem = null;
		}
		
		public WorkItem getWorkItem() {
			WorkItem result = workItem;
			workItem = null;
			return result;
		}
		
		public int getActivations() {
			return activations;
		}
	}
}
