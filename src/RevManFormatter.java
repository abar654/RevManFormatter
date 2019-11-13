import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;

public class RevManFormatter {

	public static void main(String[] args) throws IOException {
		
		//Open the file for reading
		String inputFilename = "PRPSemicolon.csv";
		File inputFile = new File(inputFilename);
		Scanner input = new Scanner(inputFile);
		
		//Open file for output of benefits
		FileWriter benefitsFile = new FileWriter("benefits.txt");
		BufferedWriter benefitsWriter = new BufferedWriter(benefitsFile);
		//Open file for output of harms
		FileWriter harmsFile = new FileWriter("harms.txt");
		BufferedWriter harmsWriter = new BufferedWriter(harmsFile);
		//Open file for output of which outcomes/timepoints we need back conversion data
		FileWriter SMDFile = new FileWriter("SMD.csv");
		BufferedWriter SMDWriter = new BufferedWriter(SMDFile);
		
		//Write the head for the SMD file
		SMDWriter.write("Comparison-Outcome-Timepoint, Back Transform Instrument, SD for Back Transform\n");
		
		//Possible outcomes:
		String[] possibleOutcomes = {	"mean pain",
										"function",
										"pain relief",
										"treatment success",
										"grip strength",
										"withdrawal",
										"adverse events"};
		
		//Set to keep track of which outcomes haven't been seen for the current comparison
		HashSet<String> unseenOutcomes = new HashSet<String>();
		
		//Remove the header line
		if(input.hasNextLine()) {
			String trash = input.nextLine();
		}
		
		//File Format is:
		//Comparison [empty in 4 & 5]
		//	Outcome 1 ["CON" in 1 & empty in 5] OR [something in Qint] OR ["DIC" in 1 & empty in 5]
		//		Timepoint 1 [If wasn't an outcome but still has something in 1]
		//		Timepoint 2
		//	Outcome 2
		
		String currentOutcome = null;
		String currentComparison = null;
		
		//Need an input buffer in case we read a line that needs to be used again
		String inputBuffer = null;
				
		while(input.hasNextLine() || inputBuffer != null) {
			
			//Get the next line
			String nextInputLine = inputBuffer;
			if(nextInputLine == null) {
				nextInputLine = input.nextLine();
			}
			inputBuffer = null;
			
			String[]csvRow = nextInputLine.split(";", -1);
			
			//Decide if the line is useful
			if(csvRow[4].equals("") && csvRow[5].equals("")) {
				
				//We have a comparison
				System.out.print(unseenOutcomes.toString());
				//Print out any outcomes that were missed in the last comparison
				for(String outcome : unseenOutcomes) {
					if(outcome.equals(possibleOutcomes[5]) || outcome.equals(possibleOutcomes[6])) {
						harmsWriter.write("   Outcome: " + outcome +"\n");
						harmsWriter.write("      None of the studies measured this outcome.\n");
					} else {
						benefitsWriter.write("   Outcome: " + outcome +"\n");
						benefitsWriter.write("      None of the studies measured this outcome.\n");
					}
				}
				
				//Reset the HashSet to be full
				unseenOutcomes = new HashSet<String>();
				for(String outcome : possibleOutcomes) {
					unseenOutcomes.add(outcome);
				}
				
				//Print out the new comparison and update
				benefitsWriter.write("Comparison: " + csvRow[0] +"\n");
				harmsWriter.write("Comparison: " + csvRow[0] +"\n");
				
				currentComparison = csvRow[0];
				
			} else if((csvRow[1].equals("CON") && csvRow[5].equals("")) ||
					(csvRow[1].equals("DIC") && csvRow[5].equals("")) ||
					(!csvRow[24].equals(""))) {
				
				//We have an outcome
				//Find out which outcome
				boolean found = false;
				for(String outcome: possibleOutcomes) {
					if(csvRow[0].toLowerCase().contains(outcome)) {
						
						currentOutcome = outcome;
						
						//In case the treatment success entry includes another keyword
						if(csvRow[0].toLowerCase().contains(possibleOutcomes[3])) {
							currentOutcome = possibleOutcomes[3];
						}
						
						if(currentOutcome.equals(possibleOutcomes[5]) || currentOutcome.equals(possibleOutcomes[6])) {
							harmsWriter.write("   Outcome: " + currentOutcome +"\n");
						} else {
							benefitsWriter.write("   Outcome: " + currentOutcome +"\n");
						}
						
						unseenOutcomes.remove(currentOutcome);
						
						//If this is dichotomous then we need to print the start of the sentence
						if(csvRow[1].equals("DIC")) {
							
							String outputString = "The " + currentOutcome + " rates were: ";
						
							//If this is one of the outcomes with no timepoints then we must print
							//the rest of its sentence.
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
								
								outputString = outputString + placeboEvents + " out of " + placeboTotal
										+ " (" + placeboPercent + "%) with placebo versus " + prpEvents
										+ " out of " + prpTotal + " (" + prpPercent + "%) with PRP (RR "
										+ effect + ", 95% CI " + upper + " to " + lower + ").";
								
							}
							
							if(currentOutcome.equals(possibleOutcomes[5]) || currentOutcome.equals(possibleOutcomes[6])) {
								harmsWriter.write("      " + outputString +"\n");
							} else {
								benefitsWriter.write("      " + outputString +"\n");
							}
							
						}
						
						found = true;
						break;
						
					}
				}
				
				if(!found) {
					//Deal with the mean pain written as "Pain" exception
					if(csvRow[0].toLowerCase().contains("pain")) {
						currentOutcome = "mean pain";
						benefitsWriter.write("   Outcome: " + currentOutcome +"\n");
						unseenOutcomes.remove(currentOutcome);
					} else {
						benefitsWriter.write("ERROR: Bad Outcome Name,  Outcome: " + csvRow[0] +"\n");
						currentOutcome = csvRow[0];
					}
				}
				
			} else if(!csvRow[1].equals("")) {

				//Find the correct timepoint
				//Take the timepoint string from the LAST occurrence of a number
				int lastOccurenceIndex = 0;
				int i;
				
				String timepoint = csvRow[0];
				
				for(i=0; i<timepoint.length(); i++) {
					if(Character.isDigit(timepoint.charAt(i)) && i!=0 && !Character.isDigit(timepoint.charAt(i-1))) {
						lastOccurenceIndex = i;
					}
				}
				
				timepoint = timepoint.substring(lastOccurenceIndex);
				
				String sentence = "";
				
				//Decide what kind of effect measure this is
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
					
					sentence = placeboEvents + " out of " + placeboTotal
							+ " (" + placeboPercent + "%) with placebo versus " + prpEvents
							+ " out of " + prpTotal + " (" + prpPercent + "%) with PRP (RR "
							+ effect + ", 95% CI " + upper + " to " + lower + ") at " + timepoint +". ";

				} else if(csvRow[3].equals("Std. Mean Difference")) {
					
					//SMD outcome
					//Put an entry for this Comparison-Outcome-Timepoint in SMD File.
					SMDWriter.write(currentComparison + "-" + currentOutcome + "-" + timepoint + ",,\n");
					
					//Need to set values for effect, upper and lower
					double effect =  Math.round(Double.parseDouble(csvRow[13])*100.0)/100.0;
					double upper = Math.round(Double.parseDouble(csvRow[15])*100.0)/100.0;
					double lower = Math.round(Double.parseDouble(csvRow[16])*100.0)/100.0;
					
					int participants = Integer.parseInt(csvRow[8]) + Integer.parseInt(csvRow[12]);
					
					//TO DO: Still need to add all this back transformed info
					String btInstrument = "[INSTRUMENT]";
					double btPlacebo = 0;
					double btEffect = 0;
					double btUpper = 0;
					double btLower = 0;
					
					//Need to set better or worse for each value depending on sign.
					String borwBtEffect = "worse";
					String borwBtUpper = "worse";
					String borwBtLower = "worse";
					
					if(btEffect < 0) {
						btEffect = btEffect*-1;
						borwBtEffect = "better";
					}
					if(btUpper < 0) {
						btUpper = btUpper*-1;
						borwBtUpper = "better";
					}
					if(btLower < 0) {
						btLower = btLower*-1;
						borwBtLower = "better";
					}
					
					sentence = "At " + timepoint + " the SMD was " + effect + " (95% CI " 
							+ upper + " to " + lower + ", " + participants + " participants), and back-transformed to the "
							+ btInstrument + ", "+ currentOutcome + " was " + btPlacebo
							+ " with placebo and " + btEffect + " points " + borwBtEffect
							+ " (95% CI " + btUpper + " " + borwBtUpper + " to " + btLower
							+ " " + borwBtLower + ") with PRP.";
					
				} else if(csvRow[3].equals("Mean Difference")) {
					
					//MD outcome
					//Need to calculate the points
					double points = 0;
					
					//Read newlines so long as they aren't a new timepoint or outcome [something in 4]
					//or new comparison [nothing in 4 and 5]
					//If we read to a new line that isn't a study then we store it in the inputBuffer
					boolean readAllStudies = false;
					
					while(!readAllStudies && input.hasNextLine()) {
						
						String studyLine = input.nextLine();
						String[] studyCSVRow = studyLine.split(";", -1);
						
						//Check if this line is a study
						if(!studyCSVRow[4].equals("") || (studyCSVRow[4].equals("") && studyCSVRow[5].equals(""))) {
							//Not a study so need to save the line
							inputBuffer = studyLine;
							readAllStudies = true;
						} else {
							//Is a study so need to add it's weighted points
							//weight is index 17, placebo mean is index 10
							points += Double.parseDouble(studyCSVRow[10]) * Double.parseDouble(studyCSVRow[17])/100;
						}
						
					}
					
					//Need to set values for effect, upper and lower
					double effect =  Math.round(Double.parseDouble(csvRow[13])*100.0)/100.0;
					double upper = Math.round(Double.parseDouble(csvRow[15])*100.0)/100.0;
					double lower = Math.round(Double.parseDouble(csvRow[16])*100.0)/100.0;
					
					//Need to set better or worse for each value depending on sign.
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
					
					int participants = Integer.parseInt(csvRow[8]) + Integer.parseInt(csvRow[12]);
					
					sentence = "At " + timepoint + ", " + currentOutcome + " was " + Math.round(points*100.0)/100.0
							+ " points with placebo and " + effect + " points " + borwEffect 
							+ " (95% CI " + upper + " " + borwUpper + " to " + lower + " " + borwLower
							+ ", " + participants + " participants) with PRP.";
				}
				
				if(currentOutcome.equals(possibleOutcomes[5]) || currentOutcome.equals(possibleOutcomes[6])) {
					harmsWriter.write("      " + sentence +"\n");
				} else {
					benefitsWriter.write("      " + sentence +"\n");
				}
				
			}
		}
		
		//Print out any outcomes that were missed in the last comparison
		for(String outcome : unseenOutcomes) {
			if(outcome.equals(possibleOutcomes[5]) || outcome.equals(possibleOutcomes[6])) {
				harmsWriter.write("   Outcome: " + outcome +"\n");
				harmsWriter.write("      None of the studies measured this outcome.\n");
			} else {
				benefitsWriter.write("   Outcome: " + outcome +"\n");
				benefitsWriter.write("      None of the studies measured this outcome.\n");
			}
		}
		
		//Then convert them to sentences: DONE
		
		//Then deal with the errors/missing data: NEED TO ASK TEEMU WHAT HE WANTS
		
		//Then clean up the sentences so that if the outcome was "Function (DASH,MM..."
		//it only prints as "function" in the sentence: DONE.
		
		//Then figure out which outcomes are missing and print their headings with the "no data" message: DONE
		
		input.close();
		benefitsWriter.close();
		harmsWriter.close();
		SMDWriter.close();
		benefitsFile.close();
		harmsFile.close();
		SMDFile.close();
	}

}
