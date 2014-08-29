package com.ohadr.cbenchmarkr;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ohadr.cbenchmarkr.interfaces.ICacheRepository;
import com.ohadr.cbenchmarkr.interfaces.ITrainee;
import com.ohadr.cbenchmarkr.utils.MailSenderWrapper;
import com.ohadr.cbenchmarkr.utils.TimedResult;


@Component
public class Manager implements InitializingBean
{
	private static Logger log = Logger.getLogger(Manager.class);

	@Autowired
	private GradesCalculator        gradesCalculator;

    @Autowired
	@Qualifier("repositoryCacheImpl")
	private ICacheRepository		repository;
    
	@Autowired
	private WorkoutMetadataContainer workoutMetadataContainer;

	@Autowired
	private MailSenderWrapper mailSenderWrapper;

	private boolean needToCalcGrades = true;
    

	@Override
	public void afterPropertiesSet() throws Exception
	{
//		addWorkout( new WorkoutMetadata(CftCalcConstants.ANNIE, CftCalcConstants.ANNIE, true) );
//		addWorkout( new WorkoutMetadata(CftCalcConstants.CINDY, CftCalcConstants.CINDY, true) );
//		addWorkout( new WorkoutMetadata(CftCalcConstants.BARBARA, CftCalcConstants.BARBARA, true) );
	}


	public void addWorkoutForTrainee(String traineeId, Workout workout) throws BenchmarkrRuntimeException 
	{
		validateWorkout( workout.getName() );
		repository.addWorkoutForTrainee( traineeId, workout );
		
		//do not re-calc all averages (@calcAveragesAndGrades()), instead use in-mem averages to calc locally the diff:
		double grade = gradesCalculator.recalcForTrainee( traineeId, workout );
		log.debug( traineeId + ": new total grade (averages were not calced!)= " + grade );
		ITrainee trainee = repository.getTraineesCache().get( traineeId );
		trainee.setTotalGrade( grade );
		
		//indicate that averages and grades need to be re-calced:
		needToCalcGrades = true;
		
		mailSenderWrapper.notifyAdmin("ohad.redlich@gmail.com",
				"cBenchmarkr: new workout registered",
				"user " + traineeId + " has registered WOD-result for " + workout.getName() );
						
	}


	/**
	 * validate the workout exists in the workouts-container.
	 * @param workoutName
	 * @throws BenchmarkrRuntimeException 
	 */
	private void validateWorkout(String workoutName) throws BenchmarkrRuntimeException 
	{
		
		if( ! getAllWorkoutsNames().contains( workoutName ) )
		{
			//ERROR
			log.error("workout " + workoutName + " does not exist in storage.");
			throw new BenchmarkrRuntimeException("workout " + workoutName + " does not exist in storage.");
		}
	}
	
	public void calcAveragesAndGrades() throws BenchmarkrRuntimeException
	{
		if( needToCalcGrades )
		{
			gradesCalculator.calcAveragesAndGrades();

			//place this AFTER the calc, so if we have exception in the calc, we will try to re-calc:
			needToCalcGrades = false;
		}
		else
		{
			log.info("no need to re-calc the averages&grades");
		}
	}

	public void addWorkout(WorkoutMetadata workoutMD)
	{
		workoutMetadataContainer.addWorkoutMetadata( workoutMD );
		
		repository.clearAveragesForWorkouts();
	}
	
	
	public Collection<ITrainee> getSortedTraineesByGrade()
	{
		List<ITrainee> trainees = repository.getTrainees();
		Collections.sort( trainees );
		return trainees;
	}
	
	public List<TimedResult> getWorkoutHistoryForTrainee( String trainee, String workoutName ) throws BenchmarkrRuntimeException
	{
		validateWorkout( workoutName );

		List<TimedResult> retVal = repository.getWorkoutHistoryForTrainee(trainee, workoutName);
		log.debug( retVal );
		return retVal;
	}
	
	public Set<String> getAllWorkoutsNames()
	{
        log.info( "getAllWorkoutsNames()" );
		return workoutMetadataContainer.getAllWorkoutsNames();
	}

	public Collection<WorkoutMetadata> getAllWorkoutsMetadata()
	{
		return workoutMetadataContainer.getAllWorkoutsMetadata();
	}


	public boolean setAdmin(String authenticatedUsername) 
	{
		if( authenticatedUsername.startsWith("ohad.redlich"))
		{
			repository.setAdmin( authenticatedUsername );
	        log.info( "trainee: " + authenticatedUsername + " was set as admin" );
			return true;
		}
		return false;
	}


	public int getNumberOfRegisteredUsers()
	{
		return repository.getNumberOfRegisteredUsers();
	}


	public void createBenchmarkrAccount(String traineeId, boolean isMale,
			Date dateOfBirth) throws BenchmarkrRuntimeException 
	{
		repository.createBenchmarkrAccount( traineeId, isMale, dateOfBirth );
		
		//if it is 'ohad.redlich' - make it admin
		setAdmin( traineeId );
	}


	public void resetDB()
	{
		repository.resetRepository();
	}


	public int getNumberOfRegisteredResults()
	{
		return repository.getNumberOfRegisteredResults();
	}


	public void updateBenchmarkrAccount(String traineeId,
			String firstName, String lastName, Date dateOfBirth)
	{
		repository.updateBenchmarkrAccount( traineeId, firstName, lastName, dateOfBirth );
	}


	public ITrainee getTraineeById(String traineeId)
	{
		return repository.getTraineeFromCache( traineeId );
	}


}