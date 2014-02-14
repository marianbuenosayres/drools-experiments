package org.jbpm.rollback;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;

@Entity
public class ProcessSnapshot {

	@Id @GeneratedValue(strategy=GenerationType.AUTO)
	private Long id;
	@Lob
	private byte[] binarySnapshot;
	private String processId;
	private Long processInstanceId;

	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public byte[] getBinarySnapshot() {
		return binarySnapshot;
	}
	
	public void setBinarySnapshot(byte[] binarySnapshot) {
		this.binarySnapshot = binarySnapshot;
	}
	
	public String getProcessId() {
		return processId;
	}
	
	public void setProcessId(String processId) {
		this.processId = processId;
	}
	
	public Long getProcessInstanceId() {
		return processInstanceId;
	}
	
	public void setProcessInstanceId(Long processInstanceId) {
		this.processInstanceId = processInstanceId;
	}
}
