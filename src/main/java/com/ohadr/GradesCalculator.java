package com.ohadr.cbenchmarkr;

import java.util.*;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ohadr.cbenchmarkr.interfaces.ITrainee;
import com.ohadr.cbenchmarkr.interfaces.IRepository;

@Component
public class GradesCalculator
{
	private static Logger log = Logger.getLogger(GradesCalculator.class);
	
	@Autowired
	@Qualifier("repositoryCacheImpl")
	private IRepository			repository;
	
	@Autowired
	private WorkoutMetadataContainer workoutMetadataContainer;

	/**
	 * maps from WOD-name to its average:
	 */
	private Map<String, Integer> averageGrades = new HashMap<String, Integer>();

	
	public void setRepositoty(IRepository repository)
	{
		this.repository = repository;
	}
	
	public void calcAveragesAndGrades() throws BenchmarkrRuntimeException
	{
		calcAverages();
		Map<String, Double> gradesPerTrainee = calcGrades();
		repository.updateGradesForTrainees( gradesPerTrainee );
	}
	
	private void calcAverages()
	{
		//all Persons And Wods Map. 'bigMap' maps from person-id to this person's results' map:
		Map< String, Map<String, Integer> > bigMap = new HashMap< String, Map<String, Integer> >();

		Collection<ITrainee> persons = repository.getAllTrainees();
		for( ITrainee person : persons )
		{
			bigMap.put( person.getId(), person.getResultsMap() );
		}
		
		//calc the averages for all wods:
		log.info("calc the averages");
		for( String wod : workoutMetadataContainer.getAllWorkoutsNames() )
		{
			log.info( "calc the averages for " + wod );
			int sum = 0;
			int workoutParticipants = 0;
			Collection<Map<String, Integer>> allWodsOfAllPersons = bigMap.values();
			for(Map<String, Integer> resultsOfSomeone : allWodsOfAllPersons)
			{
				Integer wodResult = resultsOfSomeone.get(wod);
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
		Collection<ITrainee> persons = repository.getAllTrainees();
		Map<String, Double> gradesPerTrainee = new HashMap<String, Double>();

		for( ITrainee person : persons )
		{
			log.debug("calc the grades for " + person.getId());
			int totalGradeForPerson = 0;
			int numWorkoutsForPerson = 0;

			//calc the diff:
			Map<String, Integer> results = person.getResultsMap();
			for( String workoutName : results.keySet() )
			{
				++numWorkoutsForPerson;
				int result = results.get( workoutName );
				log.debug("*** result= " + result + "averageGrades= " + averageGrades);
				log.debug( "*** workoutName= " + workoutName );
				int diff = result - averageGrades.get( workoutName );
				
				
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

				log.debug(person.getId() + ": " + workoutName + ": diff= " + diff);
			}

			double grade = (double)totalGradeForPerson / numWorkoutsForPerson;
			log.debug(person.getId() + ": total grade= " + grade);
			
			gradesPerTrainee.put(person.getId(), grade);
		}
		
		return gradesPerTrainee;		
	}
	
	public void logAverages()
	{
		log.info("log averages");
		for( Entry<String, Integer> pair : averageGrades.entrySet() )
		{
			log.info( pair.getKey() + " : " + pair.getValue() );
		}		
	}
}
