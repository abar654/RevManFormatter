import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;

public class RevManFormatter {
	
	/*
	 * RevMan is able to generate a CSV with the data in its data and analysis section.
	 * This script reads in the RevMan generated CSV and pretty prints the results
	 * with appropriate headings and formatted sentences.
	 */
	
	public static void main(String[] args) throws IOException {
		
		//Open the input file for reading
		String inputFilename = "PRPDataSemicolon.csv";
		File inputFile = new File(inputFilename);
		Scanner input = new Scanner(inputFile);
		
		//Possible outcomes from the input file
		String[] possibleOutcomes = {	"mean pain",
										"function",
										"pain relief",
										"treatment success",
										"grip strength",
										"withdrawal",
										"adverse events"};
				
		//Set to keep track of which outcomes haven't been seen for the current comparison
		HashSet<String> unseenOutcomes = new HashSet<String>();
		
		//Open file for output of benefits
		FileWriter benefitsFile = new FileWriter("benefits.txt");
		BufferedWriter benefitsWriter = new BufferedWriter(benefitsFile);
		
		//Open file for output of harms
		FileWriter harmsFile = new FileWriter("harms.txt");
		BufferedWriter harmsWriter = new BufferedWriter(harmsFile);
		
		//Open file for output of which outcomes/timepoints we need back transform data for
		FileWriter SMDFile = new FileWriter("SMD.csv");
		BufferedWriter SMDWriter = new BufferedWriter(SMDFile);
		//Write the head for the SMD file
		SMDWriter.write("Comparison-Outcome-Timepoint, Back Transform Instrument, SD for Back Transform\n");
		
		//Open the SMDInput file if it exists and create a scanner
		//This scanner will be used in the same section where the SMD.csv is created
		boolean existsSMDInput = true;
		Scanner smdInput = null;
		if(existsSMDInput) {
			String smdInputFilename = "SMDInput-21-11.csv";
			File smdInputFile = new File(smdInputFilename);
			smdInput = new Scanner(smdInputFile);
			//Remove the header line from the input
			if(smdInput.hasNextLine()) {
				String trash = smdInput.nextLine();
			}
		}
		
		//Remove the header line from the input CSV
		if(input.hasNextLine()) {
			String trash = input.nextLine();
		}
		
		//File Format is:
		//Comparison [empty in 4 & 5]
		//	Outcome 1 ["CON" in 1 & empty in 5] OR [something in Qint] OR ["DIC" in 1 & empty in 5]
		//		Timepoint 1 [If wasn't an outcome but still has something in 1]
		//		Timepoint 2
		//	Outcome 2
		
		//Keep track of the outcome and comparison we are currently working with
		String currentOutcome = null;
		String currentComparison = null;
		
		//Need an input buffer in case we read a line that needs to be used again
		String inputBuffer = null;
				
		while(input.hasNextLine() || inputBuffer != null) {
			
			//Get the next line
			//If there is something in the input buffer then use it, then clear the input buffer
			String nextInputLine = inputBuffer;
			if(nextInputLine == null) {
				nextInputLine = input.nextLine();
			}
			inputBuffer = null;
			
			//Split the csvRow into its columns
			String[]csvRow = nextInputLine.split(";", -1);
			
			//Decide if the line is a comparison, an outcome, or a timepoint
			//The decision criteria are based on patterns recognised in the csv
			//Unfortunately the csv does not have any "nice" labelling scheme for its rows
			if(csvRow[4].equals("") && csvRow[5].equals("")) {
				
				//This is a comparison row so we have a new comparison.				
				//Tidy up from the last comparison (if there was one)
				//Print out any outcomes that were missed in the last comparison
				for(String outcome : unseenOutcomes) {
					if(outcome.equals(possibleOutcomes[5]) || outcome.equals(possibleOutcomes[6])) {
						harmsWriter.write("   " + outcome +"\n");
						harmsWriter.write("      None of the studies measured this outcome.\n");
					} else {
						benefitsWriter.write("   " + outcome +"\n");
						benefitsWriter.write("      None of the studies measured this outcome.\n");
					}
				}
				
				//Reset the HashSet tracking unseen outcomes to be full
				unseenOutcomes = new HashSet<String>();
				for(String outcome : possibleOutcomes) {
					unseenOutcomes.add(outcome);
				}
				
				//Print out the new comparison and update the current comparison
				benefitsWriter.write(csvRow[0] +"\n");
				harmsWriter.write(csvRow[0] +"\n");
				currentComparison = csvRow[0];
								
			} else if((csvRow[1].equals("CON") && csvRow[5].equals("")) ||
					(csvRow[1].equals("DIC") && csvRow[5].equals("")) ||
					(!csvRow[24].equals(""))) {
				
				//This is an outcome row
				//We need to match this outcome to one of the possible outcomes
				//This is because outcomes have been entered manually 
				//and differ slightly for each comparison and may contain typos.
				boolean found = false;
				for(String outcome: possibleOutcomes) {
					if(csvRow[0].toLowerCase().contains(outcome)) {
						
						currentOutcome = outcome;
						
						//In case the treatment success entry includes another keyword
						if(csvRow[0].toLowerCase().contains(possibleOutcomes[3])) {
							currentOutcome = possibleOutcomes[3];
						}
						
						//Output this outcome's heading to the correct file
						if(currentOutcome.equals(possibleOutcomes[5]) || currentOutcome.equals(possibleOutcomes[6])) {
							harmsWriter.write("   " + currentOutcome +"\n");
						} else {
							benefitsWriter.write("   " + currentOutcome +"\n");
						}
						
						//Track that we have seen this outcome for this comparison.
						unseenOutcomes.remove(currentOutcome);
						
						//If this is dichotomous then we need to print the start of the sentence
						if(csvRow[1].equals("DIC")) {
							
							String outputString = "The " + currentOutcome + " rates were: ";
						
							//If this is one of the outcomes with no timepoints then we must print
							//the rest of its sentence.
							//Build the sentence according to the desired format.
							if(!csvRow[24].equals("")) {
								
								double effect =  Math.round(Double.parseDouble(csvRow[13])*100.0)/100.0;
								double upper = Math.round(Double.parseDouble(csvRow[15])*100.0)/100.0;
								double lower = Math.round(Double.parseDouble(csvRow[16])*100.0)/100.0;
								
								int placeboEvents = Integer.parseInt(csvRow[9]);
								int placeboTotal = Integer.parseInt(csvRow[12]);
								long placeboPercent = Math.round(100.0*placeboEvents/placeboTotal);
								
								int prpEvents = Integer.parseInt(csvRow[5]);
								int prpTotal = Integer.parseInt(csvRow[8]);
								long prpPercent = Math.round(100.0*prpEvents/prpTotal);
								
								String[] comparators = currentComparison.split("versus");
								
								if(currentComparison.contains("vs")) {
									comparators = currentComparison.split("vs");
								}
								
								//Also need to check if there is an erroneous extra line
								//If there is then we read it and forget it exists.
								if(input.hasNextLine()) {
									
									String possibleDuplicate = input.nextLine();
									String[] dupCSVRow = possibleDuplicate.split(";", -1);
									
									if(!(dupCSVRow[1].equals("DIC") && csvRow[9].equals(dupCSVRow[9]) && csvRow[12].equals(dupCSVRow[12]) 
											&& csvRow[5].equals(dupCSVRow[5]) && csvRow[8].equals(dupCSVRow[8]))) {
										inputBuffer = possibleDuplicate;
									}
									
								}
								
								//Count how many studies went into this outcome
								//The next lines in the file contain the data for each study contributing to this timepoint.
								//So we read newlines so long as they aren't a new timepoint or outcome [something in 4]
								//or new comparison [nothing in 4 and 5].
								//If we read to a new line that isn't a study then we store it in the inputBuffer
								//so that it can be appropriately handled in the next iteration of the main while loop
								//(this also means that we have found the last study related to this timepoint).
								boolean readAllStudies = false;
								int studyCount = 0;
								
								//If there is something in the inputBuffer then it will be a study
								if(inputBuffer != null) {
									studyCount++;
									inputBuffer = null;
								}
								
								while(!readAllStudies && input.hasNextLine()) {
									
									String studyLine = input.nextLine();
									String[] studyCSVRow = studyLine.split(";", -1);
									
									//Check if this line is a study
									if(!studyCSVRow[4].equals("") || (studyCSVRow[4].equals("") && studyCSVRow[5].equals(""))) {
										
										//Not a study so need to save the line
										inputBuffer = studyLine;
										readAllStudies = true;
										
									} else {
										
										//This line is a study so need to increase the study count
										studyCount++;
										
									}
									
								}
								
								String studyCountString = studyCount + " studies";
								
								if(studyCount <= 1) {
									studyCountString = "1 study";
								}							
								
								outputString = outputString + placeboEvents + " out of " + placeboTotal
										+ " (" + placeboPercent + "%) with " + comparators[1].trim() + " versus " + prpEvents
										+ " out of " + prpTotal + " (" + prpPercent + "%) with " + comparators[0].trim() + " (RR "
										+ effect + ", 95% CI " + upper + " to " + lower + ", " + studyCountString + ").";
								

							}
							
							//Output the sentence to the correct output file.
							if(currentOutcome.equals(possibleOutcomes[5]) || currentOutcome.equals(possibleOutcomes[6])) {
								harmsWriter.write("      " + outputString +"\n");
							} else {
								benefitsWriter.write("      " + outputString +"\n");
							}
							
						}
						
						//If we have already identified the outcome we need to break
						//This avoids double matching to TWO possible outcomes
						//The order of the possible outcomes is also the matching hierarchy
						found = true;
						break;
						
					}
				}
				
				//If we did not match this outcome to one of the possible outcomes we need to
				//handle it or print an error message to the user.
				if(!found) {
					
					if(csvRow[0].toLowerCase().contains("pain")) {
						
						//User has input "pain" where they mean "mean pain"
						currentOutcome = "mean pain";
						benefitsWriter.write("   " + currentOutcome +"\n");
						unseenOutcomes.remove(currentOutcome);
						
					} else {
						
						//We do not know how to handle this outcome so print an error
						//Still update the current outcome though
						//This means that the outcome will still be treated like other outcomes
						//However, the grammar in its generated sentences may not be very nice.
						System.out.println("ERROR: Bad Outcome Name,  Outcome: " + csvRow[0] +"\n");
						benefitsWriter.write("ERROR: Bad Outcome Name,  Outcome: " + csvRow[0] +"\n");
						currentOutcome = csvRow[0];
					}
				}
				
			} else if(!csvRow[1].equals("")) {

				//This is a timepoint row
				//Find the correct timepoint string to be printed.
				//This is generally the later end of the timepoints period.
				//Take the timepoint string from the LAST occurrence of a number OR greater than sign.
				int lastOccurenceIndex = 0;
				int i;
				
				String timepoint = csvRow[0];
				
				for(i=0; i<timepoint.length(); i++) {
					if((Character.isDigit(timepoint.charAt(i)) || timepoint.charAt(i)=='>') 
							&& i!=0 && !(Character.isDigit(timepoint.charAt(i-1)) || timepoint.charAt(i-1)=='>')) {
						lastOccurenceIndex = i;
					}
				}
				
				timepoint = timepoint.substring(lastOccurenceIndex);
				
				String sentence = "";
				
				//Decide what kind of effect measure this is
				//Output a sentence accordingly with the data for this timepoint
				if(csvRow[3].equals("Risk Ratio") || csvRow[3].equals("Risk Difference")) {
					
					//Dichotomous outcome
					double effect =  Math.round(Double.parseDouble(csvRow[13])*100.0)/100.0;
					double upper = Math.round(Double.parseDouble(csvRow[15])*100.0)/100.0;
					double lower = Math.round(Double.parseDouble(csvRow[16])*100.0)/100.0;
					
					int placeboEvents = Integer.parseInt(csvRow[9]);
					int placeboTotal = Integer.parseInt(csvRow[12]);
					long placeboPercent = Math.round(100.0*placeboEvents/placeboTotal);
					
					int prpEvents = Integer.parseInt(csvRow[5]);
					int prpTotal = Integer.parseInt(csvRow[8]);
					long prpPercent = Math.round(100.0*prpEvents/prpTotal);
					
					String[] comparators = currentComparison.split("versus");
					
					if(currentComparison.contains("vs")) {
						comparators = currentComparison.split("vs");
					}
					
					//Count how many studies went into this outcome
					boolean readAllStudies = false;
					int studyCount = 0;
					
					while(!readAllStudies && input.hasNextLine()) {
						
						String studyLine = input.nextLine();
						String[] studyCSVRow = studyLine.split(";", -1);
						
						//Check if this line is a study
						if(!studyCSVRow[4].equals("") || (studyCSVRow[4].equals("") && studyCSVRow[5].equals(""))) {
							
							//Not a study so need to save the line
							inputBuffer = studyLine;
							readAllStudies = true;
							
						} else {
							
							//This line is a study so need to increase the study count
							studyCount++;
							
						}
						
					}
					
					String studyCountString = studyCount + " studies";
					
					if(studyCount <= 1) {
						studyCountString = "1 study";
					}
					
					sentence = placeboEvents + " out of " + placeboTotal
							+ " (" + placeboPercent + "%) with " + comparators[1].trim() + " versus " + prpEvents
							+ " out of " + prpTotal + " (" + prpPercent + "%) with " + comparators[0].trim() + " (RR "
							+ effect + ", 95% CI " + upper + " to " + lower + ", " + studyCountString + ") at " + timepoint +". ";

				} else if(csvRow[3].equals("Std. Mean Difference")) {
					
					//SMD outcome
					//Put an entry for this Comparison-Outcome-Timepoint in SMD File.
					SMDWriter.write(currentComparison + "-" + currentOutcome + "-" + timepoint + ",,\n");
					
					//Need to set values for effect, upper and lower
					double effect =  Math.round(Double.parseDouble(csvRow[13])*100.0)/100.0;
					double upper = Math.round(Double.parseDouble(csvRow[15])*100.0)/100.0;
					double lower = Math.round(Double.parseDouble(csvRow[16])*100.0)/100.0;
					
					int participants = Integer.parseInt(csvRow[8]) + Integer.parseInt(csvRow[12]);
					
					//Initialise and set backtransform info incase there is no input data
					String btInstrument = "[INSTRUMENT]";
					double btEffect = 0;
					double btUpper = 0;
					double btLower = 0;
					
					//If SMD input data exists then we can update the backtransform variables
					if(existsSMDInput) {
						
						String btInput = smdInput.nextLine();
						String[] btInputVals = btInput.split(",");
						
						//Check that the btInput is for this outcome
						if(btInputVals[0].equals(currentComparison + "-" + currentOutcome + "-" + timepoint)) {
							
							//Extract the Instrument and the SD
							btInstrument = btInputVals[1];
							double sd = Double.parseDouble(btInputVals[2]);
							
							//Use the SD to calculate the bt values (simply multiply)
							btEffect = effect * sd;
							btUpper = upper * sd;
							btLower = lower * sd;
							
							//Round values to 2 dp
							btEffect = Math.round(btEffect*100.0)/100.0;
							btUpper = Math.round(btUpper*100.0)/100.0;
							btLower = Math.round(btLower*100.0)/100.0;
							

						} else {
							
							System.out.println("Match ERROR: " + currentComparison + "-" + currentOutcome + "-" + timepoint);
							
						}
						
					}
					
					//Need to set better or worse for each value depending on sign.
					//Negative is better.
					String borwBtEffect = "increase";
					String borwBtUpper = "worse";
					String borwBtLower = "worse";
					
					if(btEffect < 0) {
						btEffect = btEffect*-1;
						borwBtEffect = "reduction";
					}
					if(btUpper < 0) {
						btUpper = btUpper*-1;
						borwBtUpper = "better";
					}
					if(btLower < 0) {
						btLower = btLower*-1;
						borwBtLower = "better";
					}
					
					String[] comparators = currentComparison.split("versus");
					
					if(currentComparison.contains("vs")) {
						comparators = currentComparison.split("vs");
					}
					
					//Count how many studies went into this outcome
					boolean readAllStudies = false;
					int studyCount = 0;
					
					while(!readAllStudies && input.hasNextLine()) {
						
						String studyLine = input.nextLine();
						String[] studyCSVRow = studyLine.split(";", -1);
						
						//Check if this line is a study
						if(!studyCSVRow[4].equals("") || (studyCSVRow[4].equals("") && studyCSVRow[5].equals(""))) {
							
							//Not a study so need to save the line
							inputBuffer = studyLine;
							readAllStudies = true;
							
						} else {
							
							//This line is a study so need to increase the study count
							studyCount++;
							
						}
						
					}
					
					String studyCountString = studyCount + " studies";
					
					if(studyCount <= 1) {
						studyCountString = "1 study";
					}
					
					//Need to treat the subgroup analysis slightly differently here
					if(currentComparison.contains("Subgroup")) {
						
						sentence = "At 3 months, for " + currentOutcome + " the SMD was " + effect + " (95% CI " 
								+ upper + " to " + lower + ", " + studyCountString + ", " + participants + " participants), which back-transforms to a mean "
								+ borwBtEffect + " of " + btEffect + " points (95% CI " + btUpper + " " + borwBtUpper + " to " + btLower
								+ " " + borwBtLower + ") on the " + btInstrument + " scale with " + timepoint + ".";
						
					} else {
						
						sentence = "At " + timepoint + ", for " + currentOutcome + " the SMD was " + effect + " (95% CI " 
								+ upper + " to " + lower + ", " + studyCountString + ", " + participants + " participants), which back-transforms to a mean "
								+ borwBtEffect + " of " + btEffect + " points (95% CI " + btUpper + " " + borwBtUpper + " to " + btLower
								+ " " + borwBtLower + ") on the " + btInstrument + " scale with " + comparators[0].trim() + ".";
						
					}
					
				} else if(csvRow[3].equals("Mean Difference")) {
					
					//MD outcome
					//Need to calculate the points for the effect size
					//This is a weighted average of the placebo mean from each study
					double points = 0;
					
					//The next lines in the file contain the data for each study contributing to this timepoint.
					//So we read newlines so long as they aren't a new timepoint or outcome [something in 4]
					//or new comparison [nothing in 4 and 5].
					//If we read to a new line that isn't a study then we store it in the inputBuffer
					//so that it can be appropriately handled in the next iteration of the main while loop
					//(this also means that we have found the last study related to this timepoint).
					boolean readAllStudies = false;
					int studyCount = 0;
					
					while(!readAllStudies && input.hasNextLine()) {
						
						String studyLine = input.nextLine();
						String[] studyCSVRow = studyLine.split(";", -1);
						
						//Check if this line is a study
						if(!studyCSVRow[4].equals("") || (studyCSVRow[4].equals("") && studyCSVRow[5].equals(""))) {
							
							//Not a study so need to save the line
							inputBuffer = studyLine;
							readAllStudies = true;
							
						} else {
							
							//This line is a study so need to add its weighted points
							//weight is index 17, placebo mean is index 10
							points += Double.parseDouble(studyCSVRow[10]) * Double.parseDouble(studyCSVRow[17])/100;
							studyCount++;
							
						}
						
					}
					
					String studyCountString = studyCount + " studies";
					
					if(studyCount <= 1) {
						studyCountString = "1 study";
					}
					
					//Need to set values for effect, upper and lower
					double effect =  Math.round(Double.parseDouble(csvRow[13])*100.0)/100.0;
					double upper = Math.round(Double.parseDouble(csvRow[15])*100.0)/100.0;
					double lower = Math.round(Double.parseDouble(csvRow[16])*100.0)/100.0;
					
					//Need to set better or worse for each value depending on sign.
					//Negative is better.
					String borwEffect = "worse";
					String borwUpper = "worse";
					String borwLower = "worse";
					
					if(effect < 0) {
						effect = effect*-1;
						borwEffect = "better";
					}
					if(upper < 0) {
						upper = upper*-1;
						borwUpper = "better";
					}
					if(lower < 0) {
						lower = lower*-1;
						borwLower = "better";
					}
					
					String[] comparators = currentComparison.split("versus");
					
					if(currentComparison.contains("vs")) {
						comparators = currentComparison.split("vs");
					}
					
					//Participants is the sum of active group and placebo group participants
					int participants = Integer.parseInt(csvRow[8]) + Integer.parseInt(csvRow[12]);
					
					sentence = "At " + timepoint + ", " + currentOutcome + " was " + Math.round(points*100.0)/100.0
							+ " points with " + comparators[1].trim() + " and " + effect + " points " + borwEffect 
							+ " (95% CI " + upper + " " + borwUpper + " to " + lower + " " + borwLower
							+ ", " + studyCountString + ", " + participants + " participants) with " + comparators[0].trim() + ".";
				}
				
				//Output the built sentence to the correct file.
				if(currentOutcome.equals(possibleOutcomes[5]) || currentOutcome.equals(possibleOutcomes[6])) {
					harmsWriter.write("      " + sentence +"\n");
				} else {
					benefitsWriter.write("      " + sentence +"\n");
				}
				
			}
		}
		
		//There are no new comparisons so we need to tidy up from the last comparison.
		//Print out any outcomes that were missed in the last comparison.
		for(String outcome : unseenOutcomes) {
			if(outcome.equals(possibleOutcomes[5]) || outcome.equals(possibleOutcomes[6])) {
				harmsWriter.write("   " + outcome +"\n");
				harmsWriter.write("      None of the studies measured this outcome.\n");
			} else {
				benefitsWriter.write("   " + outcome +"\n");
				benefitsWriter.write("      None of the studies measured this outcome.\n");
			}
		}
		
		//Close all streams
		input.close();
		benefitsWriter.close();
		harmsWriter.close();
		SMDWriter.close();
		benefitsFile.close();
		harmsFile.close();
		SMDFile.close();
		
		if(existsSMDInput) {
			smdInput.close();
		}
	}

}
