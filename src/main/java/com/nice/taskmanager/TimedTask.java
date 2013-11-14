package com.nice.taskmanager;


public class TimedTask extends Task 
{
	private long reInsertionDate;


	
	public TimedTask(Task task) 
	{
		super(task.jobNumber, task.priority, task.interval);
		
		reInsertionDate = System.currentTimeMillis() + interval;
	}
	
//	public long getReInsertionDate() {
//		return reInsertionDate;
//	}

//	public void setReInsertionDate(Date reInsertionDate) {
//		this.reInsertionDate = reInsertionDate;
//	}

	/**
	 * @return true if time has passed and this task should be entered to the Q again.
	 */
	public boolean timeHasPassed()
	{
		return System.currentTimeMillis() >= reInsertionDate;
	}
}
