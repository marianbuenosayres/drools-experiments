package org.jbpm.rollback;

import org.drools.core.command.impl.GenericCommand;
import org.drools.core.command.impl.KnowledgeCommandContext;
import org.jbpm.persistence.ProcessPersistenceContext;
import org.jbpm.persistence.ProcessPersistenceContextManager;
import org.jbpm.persistence.processinstance.ProcessInstanceInfo;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.command.Context;

public class RewriteProcessInfoCommand implements GenericCommand<Void> {

	private static final long serialVersionUID = 3647786535301021904L;
	
	private final ProcessInstance rollbackInstance;

	public RewriteProcessInfoCommand(ProcessInstance rollbackInstance) {
		this.rollbackInstance = rollbackInstance;
	}

	public Void execute(Context context) {
		KieRuntime kruntime = ((KnowledgeCommandContext) context).getKieSession();
		Environment environment = kruntime.getEnvironment();
		ProcessPersistenceContextManager ppcm = (ProcessPersistenceContextManager)
			environment.get(EnvironmentName.PERSISTENCE_CONTEXT_MANAGER);
		ProcessPersistenceContext ppc = ppcm.getProcessPersistenceContext();
		ProcessInstanceInfo lastInfo = ppc.findProcessInstanceInfo(rollbackInstance.getId());
		lastInfo.setEnv(environment);
		try {
			java.lang.reflect.Field instanceField = ProcessInstanceInfo.class.getDeclaredField("processInstance");
			instanceField.setAccessible(true);
			instanceField.set(lastInfo, rollbackInstance);
			lastInfo.update();
		} catch (Exception e) {
			throw new ProcessRollbackException("Problem trying to repopulate ProcessInstance in database", e);
		}
		return null;
	}

}
