package com.ohadr.cbenchmarkr;

import java.util.*;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ohadr.cbenchmarkr.interfaces.ICacheRepository;
import com.ohadr.cbenchmarkr.interfaces.ITrainee;

@Component
public class GradesCalculator
{
	private static Logger log = Logger.getLogger(GradesCalculator.class);
	
	@Autowired
	@Qualifier("repositoryCacheImpl")
	private ICacheRepository			repository;
	
	@Autowired
	private WorkoutMetadataContainer workoutMetadataContainer;

	public void calcAveragesAndGrades() throws BenchmarkrRuntimeException
	{
		calcAverages();
		Map<String, Double> gradesPerTrainee = calcGrades();
		repository.updateGradesForTrainees( gradesPerTrainee );
	}
	
	private void calcAverages()
	{
		Map<String, Integer> averageGrades = repository.getAveragesForWorkouts();

		//calc the averages for all wods:
		log.info("calc the averages");
		
		Collection<ITrainee> allTrainees = repository.getTraineesCache().values();

		for( String wod : workoutMetadataContainer.getAllWorkoutsNames() )
		{
			log.debug( "calc the averages for " + wod );
			int sum = 0;
			int workoutParticipants = 0;
			
			for(ITrainee trainee : allTrainees)
			{
				Map<String, Integer> resultsOfSomeone = trainee.getResultsMap();
				Integer wodResult = resultsOfSomeone.get( wod );
				if(wodResult != null)		//in case a person did not do this workout
				{
					int result = wodResult;
					log.debug(" result: " + result );
					sum += result;
					++workoutParticipants;
				}
			}
			//cal the avg:
			int average = 0;
			if( workoutParticipants != 0 )
			{
				average = sum / workoutParticipants;		//TODO: double?
			}
			averageGrades.put(wod, average);
			log.info( "averages for " + wod + ": " + average + " (" + workoutParticipants + " participants)");
		}		
	}
	
	/**
	 * after we have calc'ed the averages, we can calc the distance from this average, per each person.
	 * @return: map from trainee-id to its grade.
	 */
	private Map<String, Double> calcGrades()
	{
		log.info("calc the grades");
		Collection<ITrainee> persons = repository.getTraineesCache().values();
		Map<String, Double> gradesPerTrainee = new HashMap<String, Double>();

		for( ITrainee person : persons )
		{
			double grade = calcGradeForTrainee( person );
			
			gradesPerTrainee.put(person.getId(), grade);
		}
		
		return gradesPerTrainee;		
	}

	private double calcGradeForTrainee(	ITrainee person )
	{
		log.info("calc the grades for " + person.getId());
		Map<String, Integer> averageGrades = repository.getAveragesForWorkouts();
		
		if( averageGrades.isEmpty() )
		{
			calcAverages();			
		}
		
		int totalGradeForPerson = 0;
		int numWorkoutsForPerson = 0;

		//calc the diff:
		Map<String, Integer> results = person.getResultsMap();
		for( String workoutName : results.keySet() )
		{
			log.debug( "calc grade for workoutName= " + workoutName );
			++numWorkoutsForPerson;
			int result = results.get( workoutName );
			int averageForWorkout = averageGrades.get( workoutName );
			int diff = result - averageForWorkout;

			log.info( " workoutName= " + workoutName + ", result= " + result + 
					", averageForWorkout= " + averageForWorkout + ", diff= " + diff);
			
			//consider type of wod: if it is TIMED (not reps), then decrease:
			WorkoutMetadata workoutMetadata = workoutMetadataContainer.getWorkoutMetadataByName( workoutName );
			if( workoutMetadata.isRepetitionBased() )
			{
				totalGradeForPerson += diff;
			}
			else
			{
				totalGradeForPerson -= diff;
			}
		}

		log.info(person.getId() + ": totalGradeForPerson= " + totalGradeForPerson + ", numWorkoutsForPerson= " + numWorkoutsForPerson);
		double grade = (double)totalGradeForPerson / numWorkoutsForPerson;
		log.info(person.getId() + ": total grade= " + grade);
		return grade;
	}
	
	/**
	 * when user updates a new WOD-result, we do not want to re-calc all averages, because it is heavy and expensive to
	 * go to DB and update each time. so we do "half the way" - calc a new grade bades on old averages, but with
	 * the new result
	 * @param trainee
	 * @param workout
	 */
	public double recalcForTrainee(String traineeId, Workout workout)
	{
		//cache was updated with the new result by the manager. so now we just re-calc the grade:
		Map<String, ITrainee> trainees = repository.getTraineesCache();
		ITrainee trainee = trainees.get( traineeId );
		double grade = calcGradeForTrainee( trainee );
		return grade;
	}
	
}
