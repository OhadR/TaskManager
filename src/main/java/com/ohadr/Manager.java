package com.ohadr.cbenchmarkr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ohadr.cbenchmarkr.interfaces.ICacheRepository;
import com.ohadr.cbenchmarkr.interfaces.ITrainee;
import com.ohadr.cbenchmarkr.utils.MailSenderWrapper;
import com.ohadr.cbenchmarkr.utils.StatisticsData;
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
		validateWorkout( workout );
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
	 * if a user has entered a wrong value to his workout, he can "override" it with a new one. but in case
	 * he has entered a workout by mistake and wants to completely erase this workout - we use this method:
	 * @param traineeId
	 * @param workout
	 * @throws BenchmarkrRuntimeException
	 */
	public void removeWorkoutForTrainee(String traineeId, Workout workout) throws BenchmarkrRuntimeException 
	{
		validateWorkout( workout );
		repository.removeWorkoutForTrainee( traineeId, workout );
		
		//do not re-calc all averages (@calcAveragesAndGrades()), instead use in-mem averages to calc locally the diff:
		double grade = gradesCalculator.recalcForTrainee( traineeId, workout );
		log.debug( traineeId + ": new total grade (averages were not calced!)= " + grade );
		ITrainee trainee = repository.getTraineesCache().get( traineeId );
		trainee.setTotalGrade( grade );
		
		//indicate that averages and grades need to be re-calced:
		needToCalcGrades = true;
		
		mailSenderWrapper.notifyAdmin("ohad.redlich@gmail.com",
				"cBenchmarkr: workout was removed",
				"user " + traineeId + " has removed WOD-result for " + workout.getName() );
						
	}

	/**
	 * validate:
	 * 1. the workout exists in the workouts-container.
	 * 2. the workout date is not before 1-1-1970.
	 * 3. the workout date is not in the future.
	 * @param workoutName
	 * @throws BenchmarkrRuntimeException 
	 */
	private void validateWorkout(Workout workout) throws BenchmarkrRuntimeException 
	{
		validateWorkoutName( workout.getName() );

		if( workout.getDate().getTime() < 0 )
		{
			//ERROR
			log.error( "date entered was before 1-1-1970 (" + workout.getDate() + ")" );
			throw new BenchmarkrRuntimeException( "date entered was before 1-1-1970." );
		}

		if( workout.getDate().after( new Date() ) )
		{
			//ERROR
			log.error( "date entered is in the future" );
			throw new BenchmarkrRuntimeException( "date entered is in the future" );
		}

	}

	/**
	 * validate the workout-name exists in the workouts-container.
	 * @param workoutName
	 * @throws BenchmarkrRuntimeException 
	 */
	private void validateWorkoutName(String workoutName) throws BenchmarkrRuntimeException 
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
			log.info("calc grades is needed - calcing averages&grades");
			gradesCalculator.calcAveragesAndGrades();

			//place this AFTER the calc, so if we have exception in the calc, we will try to re-calc:
			needToCalcGrades = false;
		}
		else
		{
			log.debug("no need to re-calc the averages&grades");
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
		validateWorkoutName( workoutName );

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


	public void createBenchmarkrAccount(String traineeId, 
			String firstName,
			String lastName,
			boolean isMale,
			Date dateOfBirth) throws BenchmarkrRuntimeException 
	{
		repository.createBenchmarkrAccount( traineeId, firstName, lastName, isMale );
		
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


	public void recordStatistics()
	{
		StatisticsData data = new StatisticsData();
		data.numberOfRegisteredUsers = getNumberOfRegisteredUsers();
		data.numberOfRegisteredResults = getNumberOfRegisteredResults();
		
		// save to DB:
		repository.recordStatistics( data );
	}

	/**
	 * 
	 * @return
	 * @throws BenchmarkrRuntimeException if statistics entity does not exist in DB.
	 */
	public Map<String, List<TimedResult>> getRegisteredStatistics() throws BenchmarkrRuntimeException
	{
		Map<String, List<TimedResult>> stats = repository.getRegisteredStatistics();
		// the ret val is based on diffs (unlike the repo, which is accumulation):
		Map<String, List<TimedResult>> retVal = new HashMap<String, List<TimedResult>>();

		for( String statsEntry : stats.keySet())
		{
			List<TimedResult> statsValues = stats.get(statsEntry);
			List<TimedResult> diffList = new ArrayList<>();
			for(int i = 1; i < statsValues.size(); ++i)
			{
				TimedResult current = statsValues.get( i );
				TimedResult previous = statsValues.get( i-1 );;

				int diff = current.result - previous.result;
				previous = current;
				TimedResult tr = new TimedResult(diff, current.timestamp);
				diffList.add( tr );				
			}
			retVal.put(statsEntry, diffList);
		}
		return retVal;
	}


	/**
	 * 
	 * @param username
	 * @throws BenchmarkrRuntimeException if username was not found in repository.
	 */
	public void userLoginSuccess(String username) throws BenchmarkrRuntimeException
	{
		repository.setUserLoginSuccess( username );
	}


	public void clearCache()
	{
		//indicate that averages and grades need to be re-calced:
		needToCalcGrades = true;

		repository.clearCache();
	}
}