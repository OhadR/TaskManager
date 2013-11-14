package com.nice.taskmanager;

public class Task 
{

	protected int jobNumber;
	protected int priority;
	
	/**
	 * 0 means "one time task", o/w it indicates the millis till next insertion:
	 */
	protected int interval;

	
	public Task(int jobNumber, int priority, int interval)
	{
		this.jobNumber = jobNumber;
		this.priority = priority;
		this.interval = interval;		
	}
	
	
	public int getJobNumber() {
		return jobNumber;
	}

	public void setJobNumber(int jobNumber) {
		this.jobNumber = jobNumber;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public int getInterval() {
		return interval;
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}
}
