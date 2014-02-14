package org.jbpm.rollback.acceptor;

import java.util.Arrays;
import java.util.List;

public class ProcessDefinitionSnapshotAcceptor implements
		ProcessSnapshotAcceptor {

	private final List<String> processDefinitionIds;
	
	public ProcessDefinitionSnapshotAcceptor(String... processDefinitions) {
		if (processDefinitions != null && processDefinitions.length > 0) {
			processDefinitionIds = Arrays.asList(processDefinitions);
		} else {
			throw new IllegalArgumentException("Must specify at least one process ID");
		}
	}
	
	public boolean accept(String processId, long processInstanceId) {
		return processDefinitionIds.contains(processId);
	}

}
