package com.nice.taskmanager;


public class TimedTask extends Task implements Comparable<TimedTask>
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

	@Override
	public int compareTo(TimedTask o) 
	{
		if(reInsertionDate == o.reInsertionDate)
			return 0;
		if(reInsertionDate < o.reInsertionDate)
			return -1;
		else 
			return 1;
	}
}
