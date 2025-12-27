/**
 * Goal: Provide an easy means of transliterating Roman letters into Alethi script using Turos's font conventions.
 * http://www.17thshard.com/forum/topic/2285-alethi-font-and-transliterator-re-launch/
 * 
 * @author Kurkistan, with significant developmental input from Turos
 * @date 09/02/2012
 * @version 1.9.5.2
 */
 
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.File;
import java.io.PrintWriter;

import java.io.IOException;
import java.util.Scanner;
import java.io.BufferedReader;

import java.util.Arrays;

//Version changes for 1.9.5.2: Fixed words starting with 'x'.

class AlethiTransliterator_1_9_5_2{

    static boolean debug_char = false;
    static boolean debug_end_e = false;
    
    static boolean remove_illegal = true;
    static boolean add_CR = true;
    
    static boolean skip_protected = true;
    static boolean retain_tags = true;
    static boolean unbounded = false;
    static String[] skip_array; //stores text kept in <safe> tags
	
	//^global booleans to turn certain parts of the program on/off
    
    /* static String Targets = "";
    static int min = 200;
    static int max = 400; */
    
    static int Count = 0;
    static boolean Counting = true; //used to count number of replace operations run
	
	
	/**
	Program flow, 1.9.4.1:
	main()
		convertText()
			readFile()			
			buildSkipArray()*
				safeSkip()
					<Recursive>
			removeCharacters()*
			periodMover()
				inAlphabet()
			spaceEnds()
			replaceLetters()
				replace()
					realReplace()
						<Recursive>
						findReplace()
			returnSkip()*
			removeSkip()*
				realReplace()
			unSpaceEnds()
		writeFile()
		allowedCharacters*

		* - Indicates possible call based on global boolean setting.
	*/
	
	/*
	Function: main
		Runs program: Asks for filename of input and writes to outfile, as well as printing out execution time run

	Parameters:
		None

	Returns:
		void
	*/
    /**
    * Any sequence of characters bracketed by <safe>[...]</safe> will be removed from the text, replaced by a simple '<' char for the duration of the program, and put back in at the end of execution
    */
    public static void main (String[] arg) throws IOException{
    
        String temp;
        boolean stringMode = false;
        if (arg.length > 0) {
            if (arg[0].equals("--string")) {
                stringMode = true;
                if (arg.length > 1) {
                    temp = arg[1];
                } else {
                    // Read from stdin
                    Scanner input = new Scanner(System.in);
                    StringBuilder sb = new StringBuilder();
                    while (input.hasNextLine()) {
                        sb.append(input.nextLine()).append("\n");
                    }
                    temp = sb.toString();
                }
            } else {
                temp = arg[0];
            }
        } else {
             Scanner input=new Scanner(System.in);
             System.out.print("Enter input file (full name of file in same directory): ");
             temp = input.next();
        }
        //temp = "Test.txt";
    
        final double startTime = System.currentTimeMillis();
        final double endTime;
        try {
            String alethi;
            if (stringMode) {
                alethi = convertTextFromString(temp);
            } else {
                alethi = convertText(temp);
                if(alethi.equals("&"))
                    return;
            }
            
            //putting carriage-returns back in to make it look pretty in Notepad.  I can't tell what else they might do.
            if(add_CR)
                for(int i = 0; i<alethi.length();i++)
                    if(alethi.charAt(i)=='\n')
                        alethi = alethi.substring(0,i)+"\r"+alethi.substring(i++,alethi.length());
            
            //writeFile(Targets,"TEMP.txt");
            
            if (stringMode) {
                // In string mode, output to stdout
                System.out.print(alethi);
            } else {
                temp = "Alethi_"+temp;
                writeFile(alethi,temp);
            }
            
            if(debug_char){
                String violations = allowedCharacters(alethi); //debugging blatant errors           
                if(!violations.equals(""))
                    System.out.println("Unauthorized sections in text (Line:Violation):"+"\n"+violations);
        }
        } finally {
          endTime = System.currentTimeMillis();
        }
        final double duration = endTime - startTime;
        
        if (!stringMode) {
            System.out.println("Execution time: "+(duration/1000)+" seconds");
        }
        
    }

	/*
	Function: convertText
		Turns English string into Roman-alphabet phonetic spelling

	Parameters:
		roman - Raw string of input file, still in roman.

	Returns:
		Roman-alphabet phonetic spelling of input string
	*/
    private static String convertText(String roman) throws IOException
    {
        roman = readFile(roman); //text file
        
        if((roman.length()==1)&&(roman.charAt(0)=='&')) //invalid input, halt program
            return "&";
        
        // Automatically protect markdown links and URLs
        roman = protectLinksAndUrls(roman);
			
		if(skip_protected)
            roman = buildSkipArray(roman);	
		
		roman = roman.toLowerCase(); //convert to lower after skippery
        
        if(remove_illegal)
            roman = removeCharacters(roman);	
        
        roman = periodMover(roman);
        
        roman = spaceEnds(roman);      

        String alethi = replaceLetters(roman);
        
        alethi = unSpaceEnds(alethi);
		
		if(skip_protected){
			alethi = returnSkip(alethi); //returns skips from array to replace '<' characters
            alethi = removeSkip(alethi);
            if(unbounded)
                System.out.println("There is at least one unbounded '<safe>'");
				alethi=alethi.substring(0,alethi.length()-1); //for the last '\n'
        }
		
		return alethi;
    }
    
    /**
     * Converts text directly from a string (not from a file).
     * 
     * @param romanText The text to convert
     * @return The transliterated text
     */
    public static String convertTextFromString(String romanText)
    {
        if(romanText == null || romanText.length() == 0)
            return "";
        
        // Add newline at beginning and end for processing consistency
        String roman = "\n" + romanText + "\n";
        
        // Automatically protect markdown links and URLs
        roman = protectLinksAndUrls(roman);
        
        if(skip_protected)
            roman = buildSkipArray(roman);
        
        roman = roman.toLowerCase(); //convert to lower after skippery
        
        if(remove_illegal)
            roman = removeCharacters(roman);
        
        roman = periodMover(roman);
        
        roman = spaceEnds(roman);

        String alethi = replaceLetters(roman);
        
        alethi = unSpaceEnds(alethi);
        
        if(skip_protected){
            alethi = returnSkip(alethi); //returns skips from array to replace '<' characters
            alethi = removeSkip(alethi);
            if(unbounded)
                System.out.println("There is at least one unbounded '<safe>'");
            alethi = alethi.substring(0, alethi.length() - 1); //for the last '\n'
        }
        
        // Remove the leading newline we added
        if(alethi.length() > 0 && alethi.charAt(0) == '\n')
            alethi = alethi.substring(1);
        
        return alethi;
    }
    
    /**
    * Load a text file contents as a <code>String<code>.
    * 
    * @param file The input file
    * @return The file contents as a <code>String</code>
    * @exception IOException IO Error
    */
    private static String readFile(String file) throws IOException
    {
        String whole = "";
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String str;
            while ((str = in.readLine()) != null) {
                whole = whole + str + '\n';
                //process(str);
            }
            in.close();
        } catch (IOException e) {
            System.out.println("File not in directory or misspelled.");
            return "&";
        }
        
        return ("\n"+whole); //Keeping an extra \n at the end and beginning for replacement ease of use, will get rid of it at end
    }
    
    /**
     * Protects URLs in markdown links [text](url) by wrapping only the URL part in <safe> tags.
     * The visible text [text] will be transliterated, but the URL and markdown syntax are preserved.
     * 
     * @param text The text to process
     * @return Text with URLs in markdown links protected
     */
    private static String protectLinksAndUrls(String text) {
        String result = text;
        int i = 0;
        
        while (i < result.length()) {
            // Look for markdown link pattern: [text](url)
            if (result.charAt(i) == '[') {
                int bracketStart = i;
                int bracketEnd = -1;
                
                // Find matching closing bracket
                for (int j = i + 1; j < result.length(); j++) {
                    if (result.charAt(j) == ']') {
                        bracketEnd = j;
                        break;
                    }
                }
                
                if (bracketEnd != -1 && bracketEnd + 1 < result.length() && result.charAt(bracketEnd + 1) == '(') {
                    // Found [text]( - now find the matching closing parenthesis
                    int parenStart = bracketEnd + 1;
                    int parenEnd = -1;
                    int depth = 1;
                    
                    for (int j = parenStart + 1; j < result.length(); j++) {
                        if (result.charAt(j) == '(') {
                            depth++;
                        } else if (result.charAt(j) == ')') {
                            depth--;
                            if (depth == 0) {
                                parenEnd = j;
                                break;
                            }
                        }
                    }
                    
                    if (parenEnd != -1) {
                        // Protect only the URL part (inside parentheses), keep brackets and parentheses
                        String url = result.substring(parenStart + 1, parenEnd); // URL without the parentheses
                        result = result.substring(0, parenStart + 1) + "<safe>" + url + "</safe>" + result.substring(parenEnd);
                        i = parenEnd + 6 + url.length() + 7; // Skip past the safe tags
                        continue;
                    }
                }
            }
            i++;
        }
        
        return result;
    }
    
	/*
	Function: removeCharacters
		Takes out non-allowed characters, replacing appropriate characters with their proper equivalent
		
	Parameters:
		body - The text to be corrected

	Returns:
		Character-pruned original text
	*/
    private static String removeCharacters(String body) 
    {
        char[] library = new char[56];
        
        library[0] = '\t'; //tab
        library[1] = '\n';
        library[2] = ' ';
        library[3] = '.';
        
        int place = 4;
        for(int i = 65; i <=90; i++)
            library[place++] = (char)i;
        for(int i = 97; i <=122; i++)
            library[place++] = (char)i;
        
        for(int i = 0; i < body.length(); i++)
            if(Arrays.binarySearch(library,body.charAt(i))<0) //I felt embarrassed by my earlier search algorithm.
                if((body.charAt(i)=='?')||(body.charAt(i)=='!'))
                    body = body.substring(0,i)+"."+body.substring(i+1,body.length());
                else if(body.charAt(i)=='-')
                    body = body.substring(0,i)+" "+body.substring(i+1,body.length());
                else if(body.charAt(i)==(char)39) //apostrophe character
                    if((i>0)&&(body.charAt(i-1)=='s')) //allowing for both Unitied States' and United States's, as an example
                        if((i<body.length()-1)&&(body.charAt(i+1)=='s')) //"-s's"
                            body = body.substring(0,i)+" A"+body.substring((i++)+2,body.length()); //" A"->"ez"
                        else
                            body = body.substring(0,i)+" A"+body.substring((i++)+1,body.length()); //"-s'"
                    else if((i<body.length()-1)&&(body.charAt(i+1)=='s')) //"-'s"
						body = body.substring(0,i)+" B"+body.substring((i++)+2,body.length()); //" B"->"z"
					else if((i<body.length()-1)&&(body.charAt(i+1)=='d')) //Contractions
						body = body.substring(0,i)+" D"+body.substring((i++)+2,body.length()); //" D"->d
					else if((i<body.length()-2)&&(body.charAt(i+1)=='v')&&(body.charAt(i+2)=='e'))
						body = body.substring(0,i)+" E"+body.substring((i++)+3,body.length()); //" E"->v
					else if((i<body.length()-2)&&(body.charAt(i+1)=='l')&&(body.charAt(i+2)=='l'))
						body = body.substring(0,i)+" F"+body.substring((i++)+3,body.length()); //" F"->l
					else if((i<body.length()-1)&&(body.charAt(i+1)=='t'))
						if((i>1))
							if(body.charAt(i-1)=='n') //section needs work
								if((body.charAt(i-2)=='e')||(body.charAt(i-2)=='o'))
									body = body.substring(0,i-1)+" G"+body.substring((i++)+2,body.length()); //" G"->nt
								else if(body.charAt(i-2)!='a') //can't covered by this
									body = body.substring(0,i)+body.substring(i--+1,body.length()); //same as normal
								else //can't covered by this
									body = body.substring(0,i-1)+" H"+body.substring((i++)+2,body.length()); //" H"->int
							else
								body = body.substring(0,i)+body.substring(i--+1,body.length()); //same as normal
						else
							body = body.substring(0,i)+body.substring(i--+1,body.length()); //same as normal
                    else
                        body = body.substring(0,i)+body.substring(i--+1,body.length()); //same as normal
                else if ((skip_protected)&&(body.charAt(i)=='<'))
					i=i; //skipping
                else
                    body = body.substring(0,i)+body.substring(i--+1,body.length());
        return body;
    }
    
	/*
	Function: periodMover
		In the Alethi alphabet, sentences start with a period '.' and don't end with anything. This models that.

	Parameters:
		body - Text to be manipulated

	Returns:
		Text with periods moved to beginning of sentences
	*/ 
    private static String periodMover(String body)
    {
        int start = 0;
        for(int i=0;i<body.length();i++)
        {
            if(body.charAt(i)=='.'){
                while((i<body.length())&&(body.charAt(i)=='.')) //multiples
                    body = body.substring(0,start)+"."+body.substring(start,i)+body.substring((i++)+1,body.length());
                while(i<body.length())
                    if(!inAlphabet(body.charAt(i)))
                        i++;
                    else if(body.charAt(i-1)=='<') //skipping
                        i+=5;
                    else if(body.charAt(i-1)=='/') //skipping
                        i+=6;
                    else
                        break; //Yes, the cardinal sin.             
                start = i;
            }
            else if(body.charAt(i)=='\n')
                start=i+1; //Doesn't allow sentences to continue after true line breaks.  Enables no-period headers and whatnot.
        }
        return body;
    }
    
	/*
	Function: inAlphabet
		Returns whether or not a character is within the lower-case roman alphabet

	Parameters:
		character - char to be checked

	Returns:
		Boolean indicating whether or not the given char is in the lower-case roman alphabet
	*/ 
    private static boolean inAlphabet(char character){
        int value = (int)character;
        
        if((value>=97)&&(value<=122)) //just checking lowercase letters
            return true;
        return false;
        
    }
	
	/*
	Function: spaceEnds
		Adds 'space' buffers around periods, <safe> and </safe> tags, and endline characters to enable easier replacement of string segments at the ends of words.

	Parameters:
		body - Text to be manipulated

	Returns:
		Text with spaces added around periods, <safe> tags, and endline charactes
	*/ 
	private static String spaceEnds(String body){
        for(int i=0;i<body.length();i++)
            if(body.charAt(i)=='.')
                body = body.substring(0,i+1)+" "+body.substring((i++)+1,body.length());
            else if(body.charAt(i)=='\n'){
                body = body.substring(0,i)+" \n "+body.substring(i+1,body.length());
                i+=2;
            }
                
        //System.out.println(body);
        return body;
    }
	
	/*
	Function: buildSkipArray
		Builds a global String[] array storing the text inside of <safe> tags, as well as removing that text from the larger body of text and replacing it with a '<'

	Parameters:
		body - Text to be read from, <safe> found in.

	Returns:
		Void.  skip_array value set
	*/ 
	private static String buildSkipArray(String body){
		String gradual = "";
		String starting_loc = "";
        int count = 0;
        int rolling_loss=0; //amount of characters lost to snipping
        int safe_size;

        for(int i = 0; i<body.length();i++)
            if(body.charAt(i)=='<'){ //skipping
				safe_size = safeLength(body.substring(i,body.length()));
				gradual += body.substring(i,i+safe_size) + "*";
				
				starting_loc += (i-rolling_loss)+"*";
				rolling_loss+=(safe_size);
				
                count++;
				i+=safe_size-1;
            }
		if(unbounded)
			body=body+"\n";
        //System.out.println(gradual);
        skip_array = new String[count];
        int safe_ind;
        int loc_ind; //index
		int start; //where to mess with the body at
        
        for(int i = 0;i<count;i++){
            safe_ind = gradual.indexOf('*');
            skip_array[i] = gradual.substring(0,safe_ind);
			gradual = gradual.substring(safe_ind+1,gradual.length());
			//System.out.println(skip_array[i]);
			
			loc_ind = starting_loc.indexOf('*');			
			start = Integer.parseInt(starting_loc.substring(0,loc_ind));
			starting_loc = starting_loc.substring(loc_ind+1,starting_loc.length()); //shear off grabbed section
			
			body = body.substring(0,start+i)+"<"+body.substring(start+i+skip_array[i].length(),body.length());
			
			// if(i==0)
				// System.out.println(body.substring(0,start+i)+"<"+body.substring(start+i+skip_array[i].length(),body.length()));
		}
		
		return body;
	}
    
	/*
	Function: safeSkip
		Returns the number of indices to be until the end of a <safe>...</safe> sequence.

	Parameters:
		clip - The tail end of a body of text, starting at a '<' character

	Returns:
		The number of indices until the ending '>', inclusive, if it exists. The number until the end of the string otherwise.
	*/ 
    private static int safeLength(String clip){ //<safe>test.</safe>...
        int skip = 0;
        if(clip.length()>=("<safe></safe>".length()))
			//System.out.println(clip.substring(0,20));
			//System.out.println(clip.substring(0,20));
            if(clip.substring(0,6).equals("<safe>"))
                for(int i=6; i < (clip.length()-("</safe>".length()));i++)
                    if(clip.charAt(i)=='<'){
                        if(clip.substring(i,i+6).equals("<safe>"))
                            i += safeLength(clip.substring(i,clip.length()))+1; //??????
							if(unbounded)
								return clip.length();
                        else if(clip.substring(i,i+7).equals("</safe>")){
                            skip=(i+7);
                            break;
                        }
                    }
                    else if(i+1>=clip.length()-("</safe>".length())){
                        skip = clip.length();
                        unbounded = true;
                    }
        return skip;
    }
	
	/*
	Function: returnSkip
		Replaces '<' characters with their corresponding <safe> blocks of text

	Parameters:
		body - The text to be manipulated.

	Returns:
		The body gets its <safe> blocks back
	*/ 
	private static String returnSkip(String body){
		int count = 0;
		int temp;
		//System.out.println(body.substring(2200,body.length()));
		for (int i=0;i<body.length();i++)
		    if(count>=skip_array.length)
		        break;
			else if(body.charAt(i)=='<'){
				temp = skip_array[count].length();
				//System.out.println(skip_array[count]);
				if(body.length()>=i+1)
					body = body.substring(0,i)+skip_array[count]+body.substring(i+1,body.length());
				else
					body = body.substring(0,i)+skip_array[count];
				count++;
				i+=temp-1;
			}
		
		//System.out.println(body.substring(2200,body.length()));
		return body;
	}
    
	/*
	Function: removeSkip
		Removes all <safe> and </safe> tags from the text

	Parameters:
		body - The text to be manipulated.

	Returns:
		The body without any <safe> or </safe> tags
	*/ 
    private static String removeSkip(String body){
        if(!retain_tags){
			skip_protected=false;
            body =  realReplace("QQQ", body,"<safe>", "");
            body = realReplace("QQQ", body,"</safe>", ""); //java didn't agree when I wanted to nest them
			skip_protected=true;
		}
        return body;
    }
    
	/*
	Function: unSpaceEnds
		Removes the 'space' buffers around periods, <safe> and </safe> tags, and endline characters to return text to proper formating.

	Parameters:
		body - Text to be manipulated

	Returns:
		Text with spaces removed from around periods, <safe> tags, and endline charactes
	*/
    private static String unSpaceEnds(String body){
        for(int i=1;i<body.length()-2;i++)
            if(body.charAt(i)=='.')
                body = body.substring(0,i+1)+body.substring(i+2,body.length());
            else if(body.charAt(i)=='\n')
                body = body.substring(0,i-1)+"\n"+body.substring((i--)+2,body.length());

        if(body.charAt(body.length()-2)=='.')
            body = body.substring(0,body.length()-1);
        else if(body.charAt(body.length()-2)=='\n')
            body = body.substring(0,body.length()-3)+"\n";
            
        return body.substring(1,body.length()-1); //clipping first/last '\n';;
    }
	
    /*
	Function: writeFile
		Writes the given string to an outfile

	Parameters:
		text - Text to be written.
		destination - Name of outfile

	Returns:
		Void, outfile written to.
	*/
    private static void writeFile(String text, String destination) throws IOException
    {
        File file = new File(destination);
        boolean exist = file.createNewFile();
        if (!exist)
        {
            System.out.println("Output file already exists.");
            System.exit(0);
        }
        else
        {
            FileWriter fstream = new FileWriter(destination);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(text);
            out.close();
            System.out.println("File created successfully.");
        }
    }
	
	/*
	Function: allowedCharacters
		Returns string of lines and types of characters which ought not be in the text upon output because Turos's Alethi font convention does not allow for them.

	Parameters:
		body - Text to be read

	Returns:
		String containing line numbers and types of violations of font conventions
	*/
    private static String allowedCharacters(String body) 
    {
        //c, q, w, x, th, sh, ch - Forbidden; I assume no lowercaseases of the special characters (C, X)
        //\n, ' ', '.', C, S/s, T/t, X,   - Allowed
        char[] library = new char[29];
        String[] pairs = {"th","sh","ch"}; //These shouldn't trigger unless I made a serious mistake in the "necessary" section.
        
        String violations = "";
        int line = 1; //for all of those +1ers out there
        
        int target_size = 2;
        int search = body.length() - target_size;
        
        for(int j = 0;j<pairs.length;j++)
            for(int i = 0; i<=search;i++)
                if(body.charAt(i)=='\n')
                    line++;
                else if(body.substring(i,i+target_size).equals(pairs[j]))
                    violations = violations + (line+":"+pairs[j]) + "; ";
        
        library[0] = '\n';
        library[1] = ' ';
        library[2] = '.';
        library[3] = 'C';
        library[4] = 'S';
        library[5] = 'T';
        library[6] = 'X';
        
        int place = 7;
        for(int i = 97; i <=122; i++){
            if((i!=99)&&(i!=113)&&(i!=119)&&(i!=120)) //c, q, w, and x
                library[place++] = (char)i;
        }
        
        line = 1; //resetting
        
        for(int i = 0;i<body.length();i++)
            if(body.charAt(i)=='\n')
                line++;
            else if(Arrays.binarySearch(library,body.charAt(i))<0) //not in library
                violations = violations + (line+":"+body.charAt(i)) + "; ";
        
        return violations;
    }
    
	/*
	Function: test
		Generic function used to test odds and ends of code.

	Parameters:
		None

	Returns:
		Void
	*/
    public static void test()
    {
        String body = "\nbutler\n";
        String target = "ap\n";
        String sub = "op\n";
        
        System.out.println(replace(body,target,sub));
        
        int target_size = target.length();
        int sub_size = sub.length();
        
        String sofar = "";
        
        int j = 2;
        
        if((target_size>=2)&&(target.charAt(target_size-2)=='p')){
            body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"pable\n"),(sub.substring(0,sub_size-1)+"uhbuhl\n"));
            body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"able\n"),(sub.substring(0,sub_size-1)+"uhbuhl\n"));
        }

        for(int i = 0; i<=body.length()-target_size;i++)
        {
            if(body.substring(i,i+target_size).equals(target))
            {
                body = body.substring(0,i)+sub+body.substring(i+target_size,body.length());
                i+=(sub_size-target_size);
            }
        }
        
        System.out.println(body);
    }
    
    /**
     * Special charaters:
        For t, use lower case t.
        For th, use capital T.
        For s, use lower case s.
        For sh, use capital S.
        For ch, use c.
        X will print a combination of k and s.
        
        For q and w, use your imagination. Technically speaking, q is a
        combination of k and u. W is basically a combination of a long u
        ("oo") and any other vowel: a e i o and short u ("uh")
     */
	 
	 /*
	Function: replaceLetters
		Body of program, replaces English spelling of text segments with phonetic spelling in Roman-alphabet

	Parameters:
		body - Text to be manipulated

	Returns:
		Text with Roman-alphabet phonetic spelling of English words.
	*/
    private static String replaceLetters(String body)
    {
        //Ease of use
        //1.3.5-Threw in an If statement in the replace function to deal with space and \n at the same time
        
        //ph
        body = replace(body,"ph","f");
        
        //anti-
        body = replace(body,".anti",".antahy");
        
        body = replace(body,".whole",".hohl");
                
        //wh
        body = replace(body,"whose","hooz");
        body = replace(body,"whom","hoom");
        body = replace(body,"who\n","hoo\n");
        body = replace(body,"where","huair"); //changed w to u
        body = replace(body,"whir","huur"); 
        body = replace(body,"wh","hu"); //Might need more permutations
        
        body = replace(body,".accr",".uhkr"); //many many many
        body = replace(body,".acci",".aksi");
        body = replace(body,".accord",".uhkawrd");
        body = replace(body,".accomp",".uhkuhmp");
        body = replace(body,".acco",".uhko");
        body = replace(body,".accustom\n",".uhkuhstuhm\n");
        body = replace(body,".accolade\n",".akuhleyd\n");
        body = replace(body,".accus",".uhkyooz");       
        body = replace(body,".accurs",".uhkurs");
        body = replace(body,".accur",".akyer");
        body = replace(body,".accum",".uhkyoom");
        body = replace(body,".accout",".uhkoot");
        body = replace(body,".accoun",".uhkoun");
        body = replace(body,".acce",".akse"); //the dreaded double c's
        body = replace(body,".ecc",".eks");
        
        body = replace(body,"ucca","uhka");
        body = replace(body,"ucco","uhko");
        body = replace(body,"uccu","uhku");
        
        body = replace(body,".occ",".uhk");
        
        body = replace(body,"ucce","uhkse");
        body = replace(body,"ucci","uhksi");
        
        body = replace(body,"occup","okyuh"); //very special case
        body = replace(body,"occa","uhkah");
        body = replace(body,"occi","oksi");
        body = replace(body,"occe","ochee"); //?
        body = replace(body,"occo","okuh");
        body = replace(body,"occu","okuh"); //Just went down the list on http://www.morewords.com/contains/cc - Useful, if laborious
        
        //E at end - Some interference possible with C's
        
        body = replace(body,".cause",".kawz");
        body = replace(body,"ause\n","awz\n");
        
        body = replace(body,"use\n","yooz\n");
        body = replace(body,"used\n","yoozd\n"); //special case
        
        //Note: Need to make sure that plurals of e-enders are covered, i.e. wives.
        
        body = replace(body,"like\n","lahyk\n");
        
        body = replace(body,"ole\n","ohl\n"); //hyperbole will suffer
        
        body = replace(body,"ose\n","ohz\n");
        
        body = replace(body,"ame\n","eym\n");
        
        body = replace(body,"ese\n","eez\n");
        body = replace(body,"have\n","hav\n");
        body = replace(body,"ave\n","eyv\n");
        
        body = replace(body,"eive\n","eev\n");
        body = replace(body,"vive\n","vahyv\n");
        body = replace(body,"ive\n","iv\n");
        
        //body = replace(body,"ever\n","ever\n");
        
        body = replace(body,"eve\n","eev\n"); //HOWEVER
        
        body = replace(body,"eever\n","ever\n");
        
        body = replace(body,"ile\n","ahyl\n");
        
        //System.out.println(replace(replace("while ","wh","hu"),"ile\n","ahyl\n"));
        //huahyl
        
        body = replace(body,"gle\n","guhl\n");
                
        body = replace(body,".key\n",".kee\n"); //special
        body = realReplace("QQQ",body,".keys\n",".kees\n");
        
        body = replace(body,"base\n","beys\n"); //And now the ends-with function on scrabblefinder.com was useful 
        body = replace(body,"case\n","keys\n");
        body = replace(body,"chase\n","Ceys\n"); //ch == C
        body = replace(body,"Case\n","Ceys\n"); //necessary?
        body = replace(body,"erase\n","ihreys\n");
        body = replace(body,"ase\n","eez\n");
        
        body = replace(body,"olve\n","olv\n");
        body = replace(body,"alve\n","ahv\n");
        body = replace(body,"elve\n","elv\n");
        
         body = replace(body,".one\n",".uuhn\n"); //sepcial
        body = replace(body,".someone\n",".suhmuuhn\n");
        body = replace(body,".anyone\n",".eneeuuhn\n");
        
        body = replace(body,"some\n","suhm\n");
        body = replace(body,".some",".suhm");
        
        body = replace(body,"comedy","komidee");
        body = replace(body,"come\n","kuhm\n"); //Need to move this up
        body = replace(body,".come",".kuhm");
        
        body = replace(body,"ome\n","ohm\n");
        
        body = replace(body,"title\n","tahytl\n");
        
        body = replace(body,"ttle\n","tl\n");
        body = replace(body,"tle\n","tl\n"); //This is what dictionary.com said to do, and I live to serve
        
        body = replace(body,".discipline\n",".disipline\n");
         body = replace(body,"cine\n","sin\n");
        body = replace(body,"ine\n","ahyn\n");
        
        body = replace(body,"done\n","duhn\n");
        body = replace(body,"none\n","nuhn\n");
        body = replace(body,"one\n","ohn\n");
        
        body = replace(body,"ake\n","eyk\n");
        
        body = replace(body,"op\n","ohp\n");
        body = replace(body,"ope\n","ohp\n");
        
        body = replace(body,"rue\n","roo\n");
        
        body = replace(body,"ife\n","ahyf\n");
        
        body = replace(body,"bead\n","beed\n");
        body = replace(body,".read\n",".reed\n");
        body = replace(body,"nead\n","need\n");
        body = replace(body,"lead\n","leed\n");
        body = replace(body,"ead\n","ed\n"); //general
        
        body = replace(body,"ade\n","eyd\n");
        
        //1.9.2.1
        body = replace(body,"heir","air"); //general rule
        body = replace(body,"eir\n","er\n");
        
        //this one's touchy, I'm just throwing in "air" exemptions to the "eer" rule where I see them
        body = replace(body,"where\n","hwair\n");
        body = replace(body,".ere\n",".air\n");
        body = replace(body,"there\n","thair\n");
        body = replace(body,"sphere\n","sfeer\n");
        body = realReplace("QQQ",body,".here\n",".heer\n");
        body = realReplace("QQQ",body,".were\n",".wur\n");
        
        body = replace(body,"sier\n","seer\n");
        body = replace(body,"shier\n","Seer\n");
        body = replace(body,"Sier\n","Seer\n");
        body = replace(body,"cier\n","seer\n");
        
        body = replace(body,".premiere\n",".primeer\n");
        
        body = replace(body,"iere\n","yair\n");
        
        body = replace(body,"soldier","sohljer");
        body = replace(body,"iere\n","yair\n");
        
        body = replace(body,".persevere\n",".pursuhveer\n");
        body = replace(body,".revere\n",".riveer\n");
        
        body = replace(body,"cere\n","seer\n");
        
        body = replace(body,".interfere\n",".interfeer\n");
        
        body = replace(body,"mmere","M");
        body = replace(body,"mere\n","meer\n");
        body = replace(body,"M","mmere");
        
        body = replace(body,".are\n",".ahr\n");
        body = replace(body,"are\n","air\n");
        
        body = replace(body,"oke\n","ohk\n");
        
        body = replace(body,"tire","tahyuhr"); //NOT \n or e
        
        body = replace(body,"aire\n","air\n");      
        //body = replace(body,"ire\n","yuhr\n"); //?
        
        body = replace(body,"ype\n","ahyp\n");
        
        body = replace(body,"urge\n","urj\n");
        body = replace(body,"erge\n","urj\n"); //Not a mistake
        body = replace(body,"arge\n","ahrj\n");
        body = replace(body,"orge\n","wrj\n");
        
        body = replace(body,"ime\n","ahym\n");
        
        body = replace(body,"sle\n","ahyl\n");
        
        body = replace(body,"promise\n","promis\n");
        body = replace(body,"aise\n","eyz\n");
        body = replace(body,"ise\n","ahyz\n");
        
        body = replace(body,"lse\n","ls\n");
        
        body = replace(body,"igue\n","teeg\n");
        body = replace(body,"igue\n","teeg\n");

        body = replace(body,"sce\n","es\n");
        
        body = replace(body,"que\n","k\n");
        
        body = replace(body,"udge\n","uhj\n");
        body = replace(body,"dge\n","j\n"); //NOT sure
        body = replace(body,"age\n","aij\n");
        
        //gue - This one was irritating, might not be right
        body = replace(body,"logue\n","awg\n");
        body = replace(body,"gogue\n","awg\n");
        body = replace(body,".morgue\n",".mawrg\n");
        body = replace(body,".fugue\n",".fyoog\n");
        body = replace(body,".segue\n",".segwey\n");
        body = replace(body,"rgue\n","rgyoo\n");
        body = replace(body,"gue\n","eeg\n");
        
        //ible, might need to generalize downtown
        body = replace(body,"ible\n","uhbuhl\n");
        
        //-nge
        //problem with sing, singer vs singe, singer not really being separable at the gerund-testing level
        body = replace(body,"finger\n","fingger\n");
        body = replace(body,"linger\n","lingger\n");
        
        body = replace(body,"finger","fingger");
        body = replace(body,"linger","lingger");
        
        body = replace(body,".anger\n",".angger\n");
        body = replace(body,".angry\n",".angree\n");//?     
        
        //body = realReplace("",body,"ringe\n","rinj\n"); //This is the best I can do for now.
        body = replace(body,".cringe\n",".krinj\n");
        body = replace(body,".fringe\n",".frinj\n");
        body = replace(body,".cringe\n",".kuhnstrinj\n");
        body = replace(body,".astringe\n",".uhstrinj\n");
        body = replace(body,".infringe\n",".infrinj\n");
        
        body = realReplace("R",body,"hinge\n","hinj\n");
        body = realReplace("R",body,".impinge\n",".impinj\n");
        body = realReplace("R",body,"winge\n","winj\n");
        body = realReplace("R",body,".binge\n",".binj\n");
        body = realReplace("",body,".tinge\n",".winj\n");
        body = realReplace("",body,".dinge\n",".dinj\n");   
        
        body = realReplace("QQQ",body,".singe\n",".sinj\n");
        body = realReplace("QQQ",body,".singed\n",".sinjed\n");
        body = realReplace("QQQ",body,".singeing\n",".sinjing\n");
        
        body = realReplace("g",body,"inging\n","J\n"); //temporary
        body = replace(body,"ing\n","I\n"); //temporary
        
        body = replace(body,"nge\n","nj\n");
        
        body = replace(body,"I","ing");
        body = replace(body,"J","inging");
        
        //END E's
        
        //s at end - 1.7.4.5 -> unneeded, I think
        //body = replace(body,"es\n","ez\n"); //Needs to go before c->s conversion, since C's are all soft S's
        
        //This is a big thing. I moved the c down mainly to allow for the s->z convertor to do it's job, and the judgement on whether or not this messes things up is pending.
        //START C 1.7 - moved so that higher number of characters in target get's preference, blocks kept cohesive
        
        //Stolen from the "necessary" bin.
        body = replace(body,"ch","C"); //Although both versions of C work, I'm assuming capitalized, so no lowercas c's are allowed in the text
        
        body = replace(body,"accent","aksent");
        
        body = replace(body,"exercise\n","eksersahyz\n");
        
        body = replace(body,".once",".wuhns");
        
        body = replace(body,"preface\n","prefis\n"); //special
        
        body = replace(body,"icise\n","uhsahyz\n");
        body = replace(body,"rcise\n","ruhsahyz\n");
        
        body = replace(body,".tacit\n",".tasit\n");
        
        body = replace(body,"ciate\n","sheeeyt\n");
        body = replace(body,"cate\n","kit\n");
        body = replace(body,"vate\n","vit\n"); //pulled from E section, might be a sign of things to come
        body = replace(body,"literate\n","literit\n");
        body = replace(body,"ate\n","eyt\n");
        
        body = replace(body,"cision\n","sizhuhn\n");        
        body = replace(body,"cise\n","sahys\n");
        body = replace(body,"cist\n","sist");
        
        body = replace(body,"duce\n","doos\n");
        body = replace(body,"uce\n","us\n");
        body = replace(body,"uces\n","usez\n"); //z incorporated
        body = replace(body,"uced\n","usst\n"); //D's
        
        body = replace(body,"came\n","keym\n");
        body = replace(body,"came","kamuh");
		
		body = replace(body,"indict","indahyt");
        
        body = replace(body,"ct","kt"); //factual
        
        body = replace(body,"tual\n","Cual\n");
        
        body = replace(body,".acid\n",".asid\n");
        body = replace(body,".aci",".uhsi");
        
        body = replace(body,"ierce\n","eers\n");
        
        body = replace(body,"ince\n","ins\n");
        
        //body = replace(body,".ance",".ahns");
        body = replace(body,".trance",".trahns");
        body = replace(body,"dance\n","dahns\n");
                
        body = replace(body,"Cance\n","Cahns\n");
        body = replace(body,"cance\n","kahns\n");
        body = replace(body,"lance\n","lahns\n");
        body = replace(body,"vance\n","vahns\n");
        
        body = replace(body,"ance\n","uhns\n");
        
        body = replace(body,"all\n","awl\n");
        
        body = realReplace("QQQ",body,".supplement\n",".suhpluhment\n"); //special case
        body = replace(body,".supp",".suhpp"); //just a general rule
        
        body = replace(body,"ape\n","eYp\n");
        
        body = replace(body,"appa","apuh");
        body = replace(body,".appear",".uhpeer");
        body = replace(body,"ppen","pen"); //double p's, might NOT be done
        body = replace(body,"pplet\n","plit\n");
        body = replace(body,"pple\n","puhl\n");
        body = replace(body,"ppl","puhl");
        body = replace(body,"upp\n","uhp");
        body = replace(body,"oppor","oper");
        body = replace(body,".opp",".ohp");
        body = replace(body,".op",".ohp");
        body = replace(body,"opp","uhp");
        body = replace(body,"ypp","ip");
        body = replace(body,"pp","p"); //Last ditch, should cover most before this
        
        body = replace(body,"tice\n","tis\n");
        body = replace(body,"arice\n","eris\n");
        body = replace(body,"orice\n","uhis\n");
        body = replace(body,"cipice\n","suhpis\n"); //patch for precipice
        body = replace(body,"ipice\n","uhpis\n");
        body = replace(body,".vice\n","vahys\n");
        body = replace(body,"vice\n","vis\n");
        body = replace(body,"ice\n","ahys\n"); //Long S.  NOT sure about \n's
        
        body = replace(body,"egy\n","ijee\n"); //possibilities/strategies fix, I have now idea how the ended up "kiez"
        
        body = replace(body,"city\n","sitee\n");
        body = replace(body,"cite\n","sahyt\n");
        
        body = replace(body,"ity\n","itee\n");
        body = replace(body,"ite\n","ahyt\n");
        
        body = replace(body,"irst\n","urst\n");
        
        body = replace(body,"ong\n","ong\n");
        body = replace(body,"ull\n","ool\n");
        
        body = replace(body,"cide\n","sahyd\n");
        
        body = replace(body,"ide\n","ahyd\n");
        
        body = replace(body,"ence\n","ens\n");
        
        body = replace(body,"rend\n","rend\n");
        
        //1.8.9 Pie-
        body = replace(body,"piety","pahyitee");
        body = replace(body,".pier\n"," peer\n");
        body = replace(body,".pie\n"," pahy\n");
        body = replace(body,".pie",".pee");
        
        body = replace(body,"ces\n","seez\n");
        body = replace(body,"cez\n","seez\n"); //Incase of S->Z        
        body = replace(body,"ce\n","s\n");      
        body = replace(body,"ci\n","sahy\n");
        
        body = replace(body,"gan\n","gahn\n");
        
        body = replace(body,"dle\n","dl\n");
        
        body = replace(body,"align\n","uhlahyn\n");
        
        body = replace(body,"oy\n","oi\n");
        
        body = replace(body,"ace\n","eys\n");
        
        body = replace(body,".ass\n",".as\n");
        body = replace(body,".ass",".uhs"); //Assoc-
        
        body = replace(body,".rely\n",".relahy\n");
        body = replace(body,"ely\n","lee\n"); //MUST BE LAST IN \N
        
        body = replace(body,".scie",".sahye"); //For Science!
        
        body = replace(body,"sciou","shuh"); //For Conscience!
        body = replace(body,"cious","shuhs"); //For Ithaca!
        body = replace(body,"scio","shuh");
        body = replace(body,"scie","shuh");
        
        body = replace(body,"ply\n","plahy\n");
		
		body = replace(body,".excellent\n",".eksuhluhnt\n"); //1.9.4.5
        
        body = replace(body,".by\n",".bahy\n");
        body = replace(body,".my\n",".mahy\n");
        body = replace(body,".die\n",".dahy\n");
        body = replace(body,".dye\n",".dahy\n");
        body = replace(body,".bye\n",".bahy\n"); //conflict
        
        body = replace(body,"hype","hahype");
        body = replace(body,"hypo","hahypo");
        body = replace(body,"hypn","hipn");
        body = replace(body,"hyphen","hahyfuhn");
        body = replace(body,"hyfen","hahyfuhn"); //ph->f        
        body = replace(body,"yp","ip");

        body = replace(body,"eYp","eyp"); //see ape->eyp    
        
        body = replace(body,"duct","duhkt");
        
        body = replace(body,"stion","sCuhn"); //1.8.9.4
        body = replace(body,"tion","Suhn"); //1.8
        body = replace(body,"ssion","Suhn"); //1.8.6
        body = replace(body,"sion","zhuhn");
        body = replace(body,"cean","Suhn");
        
        body = replace(body,".abou",".uhbou");
        body = replace(body,".aband",".uhbanduhn");
        
        body = replace(body,"ture","Cur");
        
        body = replace(body,"cies","seez"); //prophocies
        body = replace(body,"ciez","seez"); //s->z already done 
        
        body = replace(body,"iew","yoo");
        
        body = replace(body,".face",".feys");
        body = replace(body,"face","feys");
        
        //For-
        body = replace(body,".fore",".fohr");
        body = replace(body,".for",".fohr");
        
        //ore, as in fore, bore
        body = replace(body,"ore","ohr");        
                
        body = replace(body,"acen","eysuhn"); //Don't get complacent
        
        body = replace(body,"ician","ishuhn"); //musician
        body = replace(body,"cism","sizuhm"); //anglicanism
        
        body = replace(body,"cial","shul");
        body = replace(body,".acq",".akw"); //might need refinement
        body = replace(body,"cque","ke");
        body = replace(body,"acquaint","uhkweyeynt");
        
        body = replace(body,"cing","sing");
        
        //1.6.5 - odyssey test
        body = replace(body,"exce","ikse");
        body = replace(body,"excit","iksahyt");
        body = replace(body,"excis","eksahyz");
        
        body = replace(body,"ici","isi"); //Sicily
        body = replace(body,"iec","ees"); //Piece/Peace -> Pees
        body = replace(body,"eac","ees");
        
        body = replace(body,"ight","ahyt");
        
        body = replace(body,"cep","sep");
        body = replace(body,"cin","sin");
        body = replace(body,".cit",".sit");
        body = replace(body,"cip","sip");
        
        body = replace(body,".def",".dihf");
        
        body = replace(body,"cif","sif"); //NOT sure
        
        body = replace(body,"icc","ik");    

        body = replace(body,"icn","ikn");
        
        body = replace(body,"sce","SE");
        body = replace(body,"SEyp","skeyp"); 
        body = replace(body,"SE","se"); 
        body = replace(body,"sci","si");
        body = replace(body,"scy","sahy");
        //body = replace(body,"sco","sko");
        
        body = replace(body,"cea","sea");
        
        body = replace(body,"nci","nsi"); //might need refinement
        body = replace(body,"ncy","nsee");
        
        body = replace(body,"cei","see");
        body = replace(body,"cee","see");
        body = replace(body,"cent","sent"); //odyssey
        
        body = replace(body,"it\n","it\n"); //Tacked on for suffix reasons  
        body = replace(body,"ap\n","ap\n");
        
        //starting with c
        body = replace(body,".cy",".sahy");
        body = replace(body,".cir",".sur");
        body = replace(body,".cid",".sahyd");
        body = replace(body,".ci",".si");
        
        body = replace(body,".cer",".sur");
        body = replace(body,".ce",".se");
        
        body = replace(body,"ck","k");
        
        /* body = realReplace("QQQ",body,"C\n","k\n");
        body = realReplace("QQQ",body,"ch\n","k\n"); */
        
        body = replace(body,"sc","sk");
        
        body = replace(body,"cy","see"); //1.4.3 - si->see
        
        body = replace(body,"ca","ka");
        body = replace(body,"co","ko");
        body = replace(body,"cu","ku");
        body = replace(body,"ct","kt");
        body = replace(body,"cl","kl");
        body = replace(body,"cr","kr");
        body = replace(body,"ce","se"); //might want to move
        
        body = realReplace("QQQ",body,".c",".k"); //This can possibly leave lowercase c's in the text, although I think that all properly spelled words should be covered here.
        body = realReplace("QQQ",body,"c\n","k\n"); //to stop mischeif
        //END C'S
        
        body = replace(body,".odyssey\n",".oduhsee\n"); //special
        body = replace(body,"sey\n","zee\n");
        
        //Not sure where to put this section
        //ss
        body = replace(body,"ss","s");
        
        body = replace(body,".be\n",".bee\n");
        body = replace(body,".maybe\n",".meybee\n");
        
        //rom
        body = realReplace("QQQ",body,".roman\n",".rohmahn\n"); //might want to generalize "-an" suffix
        body = replace(body,"rom","rohm");
        
        //gh
        body = replace(body,"gha","gah"); //This section needs work
        body = replace(body,"gho","goh");
        
        body = replace(body,"ought","awt");
        
        body = replace(body,"though","thoh");
        body = replace(body,"bough","bou");
        body = replace(body,"cough","kof");
        body = replace(body,"igh","ahy");
        
        body = replace(body,".enough\n",".ihnuhf\n"); //special case
        
        body = replace(body,"gh\n","\n");
        
        body = replace(body,"gh","g");
        
        //to, too, two - Just a quick patch for those three words, not a general solution to any problem I can see
        body = replace(body,".to\n",".too\n");
        body = replace(body,".two\n",".too\n");
        
        //q at end
        body = realReplace("QQQ",body,"q\n","k\n");
        
        //w at end
        body = replace(body,".low\n",".loh\n");//special cases
        body = replace(body,".row\n",".roh\n");
        body = replace(body,".tow\n",".toh\n");
        body = replace(body,"ow\n","au\n");
        
        //.sy
        body = replace(body,".syr",".suhr"); //Moved up to e-enders
        body = replace(body,".syr",".sir");
        
        body = replace(body,".sly",".slahy");
        
        body = replace(body,".lying\n",".lahying\n");
        body = replace(body,".ly",".li");
        
        //sz->siz - The coward's way out. I need to sit down and make this thing more cohesive
        body = replace(body,"sz\n","siz\n");
        
        body = replace(body,"pie\n","pahy\n"); // NOT normal, aka special
        
        body = realReplace("qqq",body,".or",".awr");
        
        body = replace(body,".sky",".skahy");
        body = replace(body,".fly",".flahy");
        
        body = replace(body,".ally\n",".alahy\n");
                
        body = realReplace("qqq",body,"y\n","ee\n");        
        body = realReplace("qqq",body,"ehee\n","ehy\n");
        body = realReplace("qqq",body,"ahee\n","ahy\n");
        body = realReplace("qqq",body,"eee\n","ey\n"); //fixing issues raised by y->ee as compared to other phonetics
        
        
        body = realReplace("qqq",body,"iest\n","eeest\n");
        body = replace(body,"izen","uhzen");
        body = replace(body,"ize","ahz");
        
        body = replace(body,"able","uhbuhl");
        body = replace(body,"ably","uhblee"); //Last sweep
        
        String[] temp = {"en","st","un","c","f","g","s","t"};        
        body = replace(body,"ctable\n","kteybuhl\n"); //save the c's!
        for(int i = 0; i<temp.length;i++)
            if(temp[i].equals("c"))
                body = replace(body,"kable\n","eybuhl\n");
            else
                body = replace(body,temp[i]+"able\n","eybuhl\n");
            
        body = replace(body,"able\n","uhbuhl\n"); //This one is either "eybuhl" for a few short words or "uhbuhl" for all others
        
        body = replace(body,"ble\n","buhl\n");
		
		body = realReplace("QQQ",body,".i\n",".ahy\n");
        
        //x's
        body = replace(body,".xy",".zi");
		body = replace(body,".x",".z");
        body = replace(body,"xious","kSuhs");
        
        //apostrophe replacement, see removeCharacters()
		boolean save =skip_protected;
		skip_protected=false;

        body = replace(body,".A","ez");
        body = replace(body,".B","z");
		body = replace(body,".D","d");
		body = replace(body,".E","v");
		body = replace(body,".F","l");
		body = replace(body,".G","nt");
		body = replace(body,".H","int");
		
		skip_protected = save;
        
        //General fixer for suffixes
        //body = replace(body,"\n","\n");
        
        //The annoying part is the hodge-podgeness of English.  The only workable rout may be just to demand phonetic spelling sometimes.
        
        //Necessary --Moved down to make ease-of-use conversions easier
        body = replace(body,"th","T");
        body = replace(body,"sh","S");

        body = replace(body,"ch","C"); //took some liberties here, capitalized the C to make room for the c->k/s conversion
        
        body = replace(body,"x","X"); //Consistency - x is really a compound character of ks.
        
        body = replace(body,"qu","ku");
        
        body = replace(body,"w","u"); //exception catcher
        
        if(debug_end_e){
            body = replace(body,"e\n","Q\n"); //Just for debugging
            body = replace(body,".TQ",".Te");
            body = replace(body,".bQ",".be");
            body = replace(body,".seQ",".seee");
            body = replace(body,".mQ",".me");
            body = replace(body,"eQ\n","ee\n");
            body = replace(body,"Qy\n","ey\n");
            body = replace(body,".hQ",".he");
            body = replace(body,".shQ",".she");
        }
                
        return body;
    }
    
	/*
	Function: replace
		Buffer function for realReplace, adds on an empty string for generic case

	Parameters:
		body - Text to be searched/replaced
		target - Text to be replaced
		sub - Text to replace target

	Returns:
		Original text with target replaced by sub by realReplace
		
	See Also:
		<realReplace>
	*/
    private static String replace(String body, String target, String sub){
        return realReplace("",body,target,sub);
    }
	
	/*
	Function: realReplace
		Permutates (hopefully) all expected suffixes to replace a given string with a substitute string

	Parameters:
		sofar - Shorthand listing of the suffixes which have been added to the original target/sub comination up to this point.  "QQQ" and "qqq" used to denote a desire not to perumutate target/string suffixes at all.
		body - Text to be searched/replaced
		target - Text to be replaced
		sub - Text to replace target

	Returns:
		Text with spaces added around periods, <safe> tags, and endline charactes
	*/
    private static String realReplace(String sofar, String body, String target, String sub)
    {
        int target_size = target.length();
        int sub_size = sub.length();
        
		boolean rerun = false;
		if(target.startsWith(".")){
			rerun = true;
			target=" "+target.substring(1,target_size);
		}
		if(target.endsWith("\n")){
			rerun = true;
			target = target.substring(0,target_size-1)+" ";
		}
		if(sub.startsWith(".")){
			rerun = true;
			sub = " "+sub.substring(1,sub_size);
		}
		if(sub.endsWith("\n")){
			rerun = true;
			sub = sub.substring(0,sub_size-1)+" ";
		}
		
		if(rerun)
			return realReplace(sofar,body,target,sub);
        //As of 1.8.8.1, '.' and '\n' are only codes for ' '. Spaces will be added before and after every \n, as well as after every period, then removed at the end.
        
        //'.'==' '
        
        
         /* if((min<Count++)&&(max>Count))
            Targets+= target+"_"; */
        if(Counting)
        {
            Count++;
            // Debug output removed - was causing "Replaces Run: X" to appear in output
            // if(target.equals("w"))
            //     System.out.println("Replaces Run: "+Count);
        }        
        
        if(target.endsWith(" "))
            if(sofar.length()<=2){ //that took longer than it should have.  Anyone who can suggest improvements is welcome to try.
                /* if(target.equals(" lingered "))
                    System.out.println(target); */
//I think contains() covers it.  It saves time over endsWith() if it stops unnecessary calls to realReplace(), as long as it doesn't cut out possible permutations
                if((!sofar.contains("z"))&&(!sofar.contains("l"))&&(!sofar.contains("t"))){  
                    if(!sofar.contains("i"))// s->z
                        if((target_size>=2)&&(target.charAt(target_size-2)=='e'))
                            if((sub_size>=2)&&(sub.charAt(sub_size-2)=='e'))
                                body = realReplace(sofar+"z",body,(target.substring(0,target_size-1)+"s "),(sub.substring(0,sub_size-1)+"z "));
                            else if((sub_size>=2)&&(sub.charAt(sub_size-2)=='y'))
                                body = realReplace(sofar+"z",body,(target.substring(0,target_size-1)+"s "),(sub.substring(0,sub_size-1)+"z ")); //s->z
                            else
                                body = realReplace(sofar+"z",body,(target.substring(0,target_size-1)+"s "),(sub.substring(0,sub_size-1)+"ez ")); //s->z
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='y'))
                            if(((sub_size>=2)&&(sub.charAt(sub_size-2)=='e'))||((sub_size>=2)||(sub.substring(sub_size-2,sub_size).equals("hy"))))
                                body = realReplace(sofar+"z",body,(target.substring(0,target_size-2)+"ies "),(sub.substring(0,sub_size-1)+"z "));
                            else
                                body = realReplace(sofar+"z",body,(target.substring(0,target_size-2)+"ies "),(sub.substring(0,sub_size-1)+"iez ")); //s->z
                        else
                            body = realReplace(sofar+"z",body,(target.substring(0,target_size-1)+"s "),(sub.substring(0,sub_size-1)+"z ")); //s->z
                    
                    /* //y
                    body = realReplace("qqq",body,"ay ","ey "); //stopgap, might want to revisit
                    body = replace(body,"ey ","ey ");
                    body = realReplace("qqq",body,"oy ","oi ");
                    body = realReplace("qqq",body,"uy ","ahy ");
                    body = realReplace("qqq",body,"y ","ee "); //might need generalized in replace()
                    body = replace(body,"ty","tahy"); */
                    
                    //ly, focus on y as of 1.7.4.3 - It might need some work
                    if(target.equals("sly ")) //special case
                        body = realReplace(sofar+"l",body,(target.substring(0,target_size-1)+"ly "),(sub.substring(0,sub_size-1)+"lee "));
                    else{
                        //ly
                        if((target_size>=5)&&(target.substring(target_size-5,target_size-1).equals("able")))
                            body = realReplace(sofar+"l",body,(target.substring(0,target_size-2)+"y "),(sub.substring(0,sub_size-4)+"lee ")); //ably
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='e'))
                            body = realReplace(sofar+"l",body,(target.substring(0,target_size-1)+"ly "),(sub.substring(0,sub_size-1)+"lee "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='y'))
                            if((sub_size>=3)&&(sub.substring(sub_size-3,sub_size-1).equals("ee")))
                                body = realReplace(sofar+"l",body,(target.substring(0,target_size-3)+"ily "),(sub.substring(0,sub_size-3)+"uhlee "));
                            else
                                body = realReplace(sofar+"l",body,(target.substring(0,target_size-2)+"ily "),(sub.substring(0,sub_size-2)+"uhlee "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='o'))
                            body = realReplace(sofar+"l",body,(target.substring(0,target_size-1)+"ly "),(sub.substring(0,sub_size-1)+"lee "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='p'))
                            body = realReplace(sofar+"l",body,(target.substring(0,target_size-1)+"pily "),(sub.substring(0,sub_size-1)+"uhlee "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='t'))
                            body = realReplace(sofar+"l",body,(target.substring(0,target_size-1)+"tily "),(sub.substring(0,sub_size-1)+"uhlee "));
                        else
                            body = realReplace(sofar+"l",body,(target.substring(0,target_size-1)+"ly "),(sub.substring(0,sub_size-1)+"lee "));
                        
                        //y
                        if((target_size>=2)&&(target.charAt(target_size-2)=='a')) //might need work
                            body = realReplace(sofar+"y",body,(target.substring(0,target_size-1)+"y "),(sub.substring(0,sub_size-2)+"ey "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='e'))
                            body = realReplace(sofar+"y",body,(target.substring(0,target_size-1)+"y "),(sub.substring(0,sub_size-1)+"y "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='o'))
                            body = realReplace(sofar+"y",body,(target.substring(0,target_size-1)+"y "),(sub.substring(0,sub_size-1)+"i "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='u'))
                            body = realReplace(sofar+"y",body,(target.substring(0,target_size-1)+"y "),(sub.substring(0,sub_size-2)+"ahy "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='p'))
                            body = realReplace(sofar+"y",body,(target.substring(0,target_size-1)+"py "),(sub.substring(0,sub_size-1)+"ee "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='t'))
                            body = realReplace(sofar+"y",body,(target.substring(0,target_size-1)+"ty "),(sub.substring(0,sub_size-1)+"ee "));
                        else
                            body = realReplace(sofar+"l",body,(target.substring(0,target_size-1)+"ly "),(sub.substring(0,sub_size-1)+"lee ")); //might not be needed
                    }
                    
                    if((!sofar.contains("g"))&&(!sofar.contains("i"))&&(!sofar.contains("r"))){ //covers multiple
                    
                        //ing, gerunds
                        if((target_size>=3)&&(target.substring(target_size-3,target_size-1).equals("ie")))
                            body = realReplace(sofar+"g",body,(target.substring(0,target_size-3)+"ying "),(sub.substring(0,sub_size-1)+"ing ")); //replacing 'ie' before gerund
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='r')){ //experiment
                            body = realReplace(sofar+"g",body,(target.substring(0,target_size-2)+"ring "),(sub.substring(0,sub_size-1)+"ring ")); //rr
                            body = realReplace(sofar+"g",body,(target.substring(0,target_size-1)+"ing "),(sub.substring(0,sub_size-1)+"ing ")); //have to do both, sadly
                        }
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='e'))
                            body = realReplace(sofar+"g",body,(target.substring(0,target_size-2)+"ing "),(sub.substring(0,sub_size-1)+"ing ")); //removing 'e'
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='p'))
                            body = realReplace(sofar+"g",body,(target.substring(0,target_size-1)+"ping "),(sub.substring(0,sub_size-1)+"ing "));
                        else if((target_size>=2)&&(target.charAt(target_size-2)=='t'))
                            body = realReplace(sofar+"g",body,(target.substring(0,target_size-1)+"ting "),(sub.substring(0,sub_size-1)+"ing "));
                        else
                            body = realReplace(sofar+"g",body,(target.substring(0,target_size-1)+"ing "),(sub.substring(0,sub_size-1)+"ing ")); //no e, presumably ends in consonant
                        
                        if((!sofar.contains("a"))&&(!sofar.contains("d"))) //ish
                            if(((target_size>=3)&&(!target.substring(target_size-3,target_size-1).equals("ly")))||(target_size<3))
                                if((target_size>=2)&&(target.charAt(target_size-2)=='p'))
                                    body = realReplace(sofar+"i",body,(target.substring(0,target_size-1)+"pish "),(sub.substring(0,sub_size-1)+"ish "));
                                else if((target_size>=2)&&(target.charAt(target_size-2)=='t'))
                                    body = realReplace(sofar+"i",body,(target.substring(0,target_size-1)+"tish "),(sub.substring(0,sub_size-1)+"ish "));
                                else if(((target_size>=3)&&(!target.substring(target_size-3,target_size-1).equals("ed")))||(target_size<3))
                                    body = realReplace(sofar+"i",body,(target.substring(0,target_size-1)+"ish "),(sub.substring(0,sub_size-1)+"ish "));
                        
                        if(!sofar.contains("a")) //able                             
                            if((target_size>=2)&&(target.charAt(target_size-2)=='p')){
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"pable "),(sub.substring(0,sub_size-1)+"uhbuhl "));
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"able "),(sub.substring(0,sub_size-1)+"uhbuhl "));
                            }
                            else if((target_size>=2)&&(target.charAt(target_size-2)=='t')){
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"table "),(sub.substring(0,sub_size-1)+"uhbuhl "));
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"able "),(sub.substring(0,sub_size-1)+"uhbuhl "));
                            }
                            else if((target_size>=2)&&(target.charAt(target_size-2)=='r')){//experiment
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"rable "),(sub.substring(0,sub_size-1)+"uhbuhl "));
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"able "),(sub.substring(0,sub_size-1)+"uhbuhl "));
                            }
                            else if(((target_size>=3)&&(!target.substring(target_size-3,target_size-1).equals("ly")))||(target_size<3))
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"able "),(sub.substring(0,sub_size-1)+"uhbuhl "));
                            else if(target.equals("fly")||target.equals("unfly"))
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"able "),(sub.substring(0,sub_size-1)+"uhbuhl "));
                            else if(((target_size>=4)&&(target.substring(target_size-4,target_size-1).equals("ing")))||(target_size<4))
                                body = realReplace(sofar+"a",body,(target.substring(0,target_size-1)+"able "),(sub.substring(0,sub_size-1)+"eybuhl "));
                        
                        //1.9
                        //ize
                        if(!sofar.contains("x"))
                            if((target_size>=2)&&(target.charAt(target_size-2)=='y'))
                                body = realReplace(sofar+"x",body,(target.substring(0,target_size-2)+"ize "),(sub.substring(0,sub_size-1)+"ahyz ")); //removing 'e'
                            else
                                body = realReplace(sofar+"x",body,(target.substring(0,target_size-1)+"ize "),(sub.substring(0,sub_size-1)+"ahyz "));
                        
                        //est - was iest before 1.9.1.1
                        if((!sofar.contains("t")))
                            if((target_size>=2)&&(target.charAt(target_size-2)=='y'))
                                body = realReplace(sofar+"t",body,(target.substring(0,target_size-2)+"iest "),(sub.substring(0,sub_size-1)+"eeest ")); //removing 'y'
                            else if((target_size>=2)&&(target.charAt(target_size-2)=='e'))
                                body = realReplace(sofar+"t",body,(target.substring(0,target_size-2)+"est "),(sub.substring(0,sub_size-1)+"est "));
                            else
                                body = realReplace(sofar+"t",body,(target.substring(0,target_size-1)+"est "),(sub.substring(0,sub_size-1)+"est "));
                    }
                    
                    if((!sofar.contains("g"))&&(!sofar.contains("d"))){ //covers multiple
                        if(target_size>=2) //d at end
                            if(target.charAt(target_size-2)=='e')
                                if((target_size>=3)&&(target.charAt(target_size-3)=='c'))
                                    body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"d "),(sub.substring(0,sub_size-1)+"st "));
                                else
                                    body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"d "),(sub.substring(0,sub_size-1)+"ed ")); //NOT st
                            else if(target.charAt(target_size-2)=='s')
                                body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"ed "),(sub.substring(0,sub_size-1)+"ed "));
                            else if(target.charAt(target_size-2)=='r'){//experiment
                                body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"red "),(sub.substring(0,sub_size-1)+"d "));
                                body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"ed "),(sub.substring(0,sub_size-1)+"d "));
                            }
                            else if((target_size>=3)&&(target.substring(target_size-3,target_size-1).equals("se")))
                                body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"d "),(sub.substring(0,sub_size-1)+"ed "));
                            else if((target_size>=2)&&(target.charAt(target_size-2)=='p'))
                                body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"ped "),(sub.substring(0,sub_size-1)+"ed "));
                            else if((target_size>=2)&&(target.charAt(target_size-2)=='t'))
                                body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"ted "),(sub.substring(0,sub_size-1)+"ed "));
                            else if((target.charAt(target_size-2)!='s')||((target_size>=3)&&(target.substring(target_size-3,target_size-1).equals("ss"))))
                                body = realReplace(sofar+"d",body,(target.substring(0,target_size-1)+"ed "),(sub.substring(0,sub_size-1)+"ed "));
                        
                        //er
                        if((!sofar.contains("r"))&&(!sofar.contains("R"))) //inge special
                            if((target_size>=2)&&(target.charAt(target_size-2)=='e'))
                                body = realReplace(sofar+"r",body,(target.substring(0,target_size-1)+"r "),(sub.substring(0,sub_size-1)+"er ")); //removing 'e'
                            else if((target_size>=2)&&(target.charAt(target_size-2)=='p'))
                                body = realReplace(sofar+"r",body,(target.substring(0,target_size-1)+"per "),(sub.substring(0,sub_size-1)+"er "));
                            else if((target_size>=2)&&(target.charAt(target_size-2)=='r')){ //experiement
                                body = realReplace(sofar+"r",body,(target.substring(0,target_size-1)+"rer "),(sub.substring(0,sub_size-1)+"rer "));
                                body = realReplace(sofar+"r",body,(target.substring(0,target_size-1)+"er "),(sub.substring(0,sub_size-1)+"er "));
                            }
                            else if((target_size>=2)&&(target.charAt(target_size-2)=='t'))
                                body = realReplace(sofar+"r",body,(target.substring(0,target_size-1)+"ter "),(sub.substring(0,sub_size-1)+"er "));
                            else
                                body = realReplace(sofar+"r",body,(target.substring(0,target_size-1)+"er "),(sub.substring(0,sub_size-1)+"er "));                           
                    }
                    
                    
                        
                    
                    
                    /* //ate, not bothering with fobiddances - Never mind
                    if((target_size>=2)&&(target.charAt(target_size-2)=='e'))
                        body = realReplace(sofar+"r",body,(target.substring(0,target_size-1)+"r\n"),(sub.substring(0,sub_size-1)+"er\n")); //removing 'e'
                    else
                        body = realReplace(sofar+"r",body,(target.substring(0,target_size-1)+"er\n"),(sub.substring(0,sub_size-1)+"er\n")); */
                    
                    
                    
                    //Why do these need to be dealt with here?
                        //Because these permuations need to be available to figure out which \n grammars to apply
                        
                    //ed, ish, ly, ing, able, edly, ishly, ably, lying, eding, abling
                        //Dirty method - add a recursion counter to replace()
                            //6 max - ed ish ly ing able z
                                //ablingly, lyingly - 3
                                //ablinger
                                
                    //s-z, ly-l, ing-g, d-d, ish-i, able-a
                        //everything abides i, nothing abides s/l //nevermind, not much likes i either
                        //a allows l/s/d,
                        
                        //a forbids a, i
                        //d forbids d, i
                        //g forbids d, g, i, a
                        //i forbids s, g, i, a 
                    
                    //er-r
                    //r forbids g, i, a, r
                    //r is forbidden by s, l, g, d
                    
                    //y-y
                    //Not messing with forbidding now (1.8.8.2)
                    
                    //x-ized, t-iest, t forbids all, don't care about anything else right now
                    
                    //I think that forbiddance is total - no forbidden suffixes at any point before
                }
            }
           
        return findReplace(body,target,target_size,sub,sub_size);
    }
    
	/*
	Function: findReplace
		Bog standard search/replace function for a given string and a given pair of target/substitute.
	Parameters:
		body - Text to be searched/replaced
		target - Text to be replaced
		target_size - Precalulated length of target string
		sub - Text to replace target
		sub_size - Precalulated length of sub string

	Returns:
		Text with target switched with sub
	*/
    private static String findReplace(String body, String target, int target_size, String sub, int sub_size){
        for(int i = 0; i<=body.length()-target_size;i++){
            for(int j = 0; j <target_size; j++)
                if(body.charAt(i+j)!=target.charAt(j))
                    break; //Once more unto the break
                else if(j+1>=target_size){
                    body = body.substring(0,i)+sub+body.substring(i+target_size,body.length());
                    i+=(sub_size-target_size);
                }
        }
    
        return body;
    }
}