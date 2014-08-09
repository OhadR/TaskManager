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

import com.ohadr.cbenchmarkr.interfaces.ITrainee;
import com.ohadr.cbenchmarkr.interfaces.IRepository;
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
	private IRepository				repository;
    
	@Autowired
	private WorkoutMetadataContainer workoutMetadataContainer;

	@Autowired
	private MailSenderWrapper mailSenderWrapper;
    

	@Override
	public void afterPropertiesSet() throws Exception
	{
//		addWorkout( new WorkoutMetadata(CftCalcConstants.ANNIE, CftCalcConstants.ANNIE, true) );
//		addWorkout( new WorkoutMetadata(CftCalcConstants.CINDY, CftCalcConstants.CINDY, true) );
//		addWorkout( new WorkoutMetadata(CftCalcConstants.BARBARA, CftCalcConstants.BARBARA, true) );
	}


	public void addWorkoutForTrainee(String trainee, Workout workout) throws BenchmarkrRuntimeException 
	{
		validateWorkout( workout.getName() );
		repository.addWorkoutForTrainee( trainee, workout );
		
//		calcAveragesAndGrades();
		
		mailSenderWrapper.notifyAdmin("ohad.redlich@gmail.com",
				"cBenchmarkr: new workout registered",
				"a new user has registered WOD-result" );
		
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
		gradesCalculator.calcAveragesAndGrades();
	}

	public void addWorkout(WorkoutMetadata workoutMD)
	{
		workoutMetadataContainer.addWorkoutMetadata( workoutMD );
	}
	
	
	public Collection<ITrainee> getSortedTraineesByGrade()
	{
		Collection<ITrainee> trainees = repository.getAllTrainees();
		List<ITrainee> traineesAsList = (List<ITrainee>) trainees;
		Collections.sort( traineesAsList );
		return traineesAsList;
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
        log.info( "traineeId: " + traineeId + ", isMale? " + isMale + ", DOB=" + dateOfBirth );
		repository.createBenchmarkrAccount( traineeId, isMale, dateOfBirth );
	}


	public void resetDB()
	{
		repository.resetRepository();
	}


	public int getNumberOfRegisteredResults()
	{
		return repository.getNumberOfRegisteredResults();
	}


}