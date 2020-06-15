package messageParser;
import java.sql.*;
import java.util.*;
import java.io.*;
import javax.xml.parsers.*;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


class messageParserQuery {
	
public String inFileName;
public String outFileName;
public static int delimiterSelected = 0;
public static String[] delimiters = {"	",","};
static String messageHeader = "Truck Number,Created on,Sent on,Sender,Message type,Message";
static HashMap<Integer, String> vehicleVidLookup;
static HashMap<Integer, String> userUidLookup;
public static String staticTruckNumber;
static int inputLines=0;
static int n=0;

public messageParserQuery (String fileName) {
	inFileName=fileName;
	vehicleVidLookup = new HashMap<Integer, String>();
	userUidLookup = new HashMap<Integer, String>();
}
public messageParserQuery (String fileName,String truckNumberString) {
	inFileName=fileName;
	staticTruckNumber = truckNumberString;
	vehicleVidLookup = new HashMap<Integer, String>();
	userUidLookup = new HashMap<Integer, String>();
}

public int getDelimiter () {
	return delimiterSelected;
}
public void setDelimiter (int newDelimiter) {
		delimiterSelected = newDelimiter;
	return;
}
public String getOutfilename () {
	return outFileName;
}

public String getStaticTruckNumber () {
	return staticTruckNumber;
}
public void setStaticTruckNumber (String newStaticTruckNumber) {
		staticTruckNumber = newStaticTruckNumber;
	return;
}
public void parseData(){
	BufferedReader inBuffer = null; //input buffer
	BufferedWriter outBuffer = null; //output buffer
    try {
		String rawText;
		String extensionString="";
		inBuffer = new BufferedReader(new FileReader(inFileName));
		if (inFileName.lastIndexOf(".") != -1)
		{
			extensionString = inFileName.substring(inFileName.lastIndexOf("."),inFileName.length());
		}
		outFileName = inFileName.replace(extensionString, "") + "_parsed" + ".csv";
		outBuffer = new BufferedWriter(new FileWriter(outFileName));
		ArrayList<String> fieldList = new ArrayList<String>();
		
		outBuffer.write(messageHeader + "\n");
		String outString = "";
		boolean lineBreak = false;
		ArrayList<String> fieldListInProgress=null;
		while ((rawText = inBuffer.readLine()) != null ) {
			inputLines++;
			if (inputLines == 1 && rawText.contains("time_confirmed"))
				System.out.println("Skipping header line");
			else {
				fieldList = rawTextToArrayList(rawText);
				if (fieldList==null || fieldList.toString().equalsIgnoreCase("[﻿asn, vid, time_created, time_sent, time_read, time_confirmed, email_addr, uid, subject, priority, direction, message_type, body_small, body_big]"))
					System.out.println("No fields contained in line " + inputLines);
				else if (fieldList.size() == 14 && fieldList.get(13).contains("</pnetmessage>")) {
					if (lineBreak) // truncated data? yeah probs, process 'broken' line first
					{
						System.out.println("Broken line processing failed, dumping what we have.");
						outString = parseMessageTableFields(fieldListInProgress);
						outBuffer.write(sanitizeOutput(outString) + "\n");
						lineBreak=false;
					}
					outString = parseMessageTableFields(fieldList);
					outBuffer.write(sanitizeOutput(outString) + "\n");
				} else if (fieldList.size()>=13) {
					if (lineBreak) // broken line interrupted by a new broken line
					{
						System.out.println("Broken line processing failed, dumping what we have.");
						outString = parseMessageTableFields(fieldListInProgress);
						outBuffer.write(sanitizeOutput(outString) + "\n");
					}
					lineBreak=true;	
					fieldListInProgress = new ArrayList<String>(fieldList);
					System.out.println("Line Break detected at line " + inputLines);
				} else if (lineBreak) {
					System.out.println("Continuing Broken line at " + inputLines);
					if (fieldListInProgress.size() == 14)  {
						fieldListInProgress.set(13,fieldListInProgress.get(13) + " "+ fieldList.get(0));
						
						if (fieldListInProgress.get(13).contains("</pnetmessage>"))
						{
							System.out.println("Broken line processing completed, writing to file.");
							outString = parseMessageTableFields(fieldListInProgress);
							outBuffer.write(sanitizeOutput(outString) + "\n");
							lineBreak=false;	
						}
					} else {
						if (fieldList.size()==1) //break was on bodysmall and we haven't run into another tab yet
						{
							fieldListInProgress.set(12,fieldListInProgress.get(12) +" "+ fieldList.get(0));
						} else { //hit a tab, make another element to create bodybig placeholder
							fieldListInProgress.set(12,fieldListInProgress.get(12) +" "+ fieldList.get(0));
							fieldListInProgress.add(fieldList.get(1));
						}
					}
				}
				else {
					System.out.println("Incorrect number of fields for input line " + inputLines + ", skipping. Raw content: ");
					System.out.println(rawText.trim());
					System.out.println(fieldList.toString());
				}
			}
		}
		outBuffer.close();
	} catch (IOException e) {
		e.printStackTrace();
	}catch (Exception e2) {
		System.out.println("parseData error on line " + inputLines +": ");
		System.out.println(e2.toString());
	} finally {
		try {
			if (inBuffer != null) inBuffer.close();
		} catch (IOException crunchifyException) {
			crunchifyException.printStackTrace();
		}
	}
}

// convert CSV to ArrayList using Split
	public static ArrayList<String> rawTextToArrayList(String rawText) {
		try {
	   ArrayList<String> output = new ArrayList<String>();
	   String otherThanQuote = " [^\"] ";
   String quotedString = String.format(" \" %s* \" ", otherThanQuote);
   String regex = String.format("(?x) "+ // enable comments, ignore white spaces
           ",                         "+ // match delimiter
           "(?=                       "+ // start positive look ahead
           "  (                       "+ //   start group 1
           "    %s*                   "+ //     match 'otherThanQuote' zero or more times
           "    %s                    "+ //     match 'quotedString'
           "  )*                      "+ //   end group 1 and repeat it zero or more times
           "  %s*                     "+ //   match 'otherThanQuote'
           "  $                       "+ // match the end of the string
           ")                         ", // stop positive look ahead
           otherThanQuote, quotedString, otherThanQuote);
   		String[] splitData=null;
		if (rawText != null) {
			if (delimiterSelected==1)
				splitData = rawText.split(regex, -1); 
			else if (delimiterSelected==0)
				splitData = rawText.split("	", -1); 
			else
				return null;
			for (int i = 0; i < splitData.length; i++) {
				if (!(splitData[i] == null) || !(splitData[i].length() == 0)) {
						output.add(splitData[i].trim()); //trim trailing/leading whitespace
					}
				
			}
		}
		return output;
		} catch (Exception e) {
			System.out.println("rawTextToArrayList: error parsing input file on line "+inputLines+".");
			System.out.println(e.toString());
			System.out.println(rawText);
			return null;
		}
	}
	
public static String parseMessageTableFields (ArrayList<String> fields) {
	String bodyBig = "";
	String bodySmall = "";
	int messageType = 0;
	String outString="";
	try {
		int vid=Integer.valueOf(fields.get(1)); 
		String timeCreated=fields.get(2);
		String timeSent=fields.get(3);
		//String timeRead=fields.get(4);
		//String timeConfirmed=fields.get(5);
		String email=fields.get(6);
		int uid=Integer.valueOf(fields.get(7).replace("NULL", "-1"));
		//int priority=Integer.valueOf(fields.get(9));
		int direction=Integer.valueOf(fields.get(10));
		messageType=Integer.valueOf(fields.get(11));
		bodySmall=fields.get(12).replace(" | (more...)", "");
		if (fields.size() == 14)
			bodyBig=fields.get(13);
		String truckNumber;
		if (staticTruckNumber==null || staticTruckNumber=="" || staticTruckNumber.isEmpty())
			truckNumber = lookupVehicle(vid);
		else
			truckNumber = staticTruckNumber;
		outString = truckNumber + "," + timeCreated + "," + timeSent.replace("NULL", " ") + ",";
		if (direction == 0)
			outString = outString + "Driver" + ",";
		else
			if (uid == -1)
				outString = outString + email + ",";
			else
				outString = outString + lookupUser(uid) + ",";
		
		if (messageType == 6)
			outString = outString + "Automatic Message" + ",";
		else if (direction == 0)
			outString = outString + "Driver Message" + ",";
		else if (direction == 1)
			outString = outString + "Dispatcher Message" + ",";
		else
			outString = outString + "Unknown" + ",";
	} catch (Exception e) {
		System.out.println("parseMessageTableFields error:");
		System.out.println(e.toString());
		System.out.println("Line: " + inputLines + ", raw content:");
		System.out.println(fields.toString());
	}
	if (messageType == 1 || messageType == 6)
		try {/* shouldn't need to sanitize if data pulled correctly
			if (!bodyBig.contains("</msg_content></ffm></pnetmessage>"))
				bodyBig = bodyBig + "</msg_content></ffm></pnetmessage>";*/
			bodyBig = bodyBig.replace("NULL", " ");
			String parsedMessage=parseMessage(bodyBig);
			if (bodySmall.length() > parsedMessage.length())
			{
				System.out.println("Using BodySmall due to ffm message body small being larger than bodybig, line "+inputLines+", bbig content: ");
				System.out.println(parsedMessage);
				parsedMessage=bodySmall;
			}
			outString = (outString + "\"" + parsedMessage + "\"").replace("\"\"", "\"");
		} catch (SAXException e1) {
			System.out.println("Using BodySmall due to XML tag mismatch, line "+inputLines+", XML content:");
			System.out.println(bodyBig);
			outString = (outString + "\"" + bodySmall + "\"").replace("\"\"", "\"");
			System.out.println(e1.toString());
		} catch (IOException e2) {
			System.out.println("Using BodySmall due to XML tag mismatch, line "+inputLines+", XML content:");
			System.out.println(bodyBig);
			System.out.println(e2.toString());
			outString = (outString + "\"" + bodySmall + "\"").replace("\"\"", "\"");
		}
	else if (messageType == 2 || messageType==3 ||messageType == 4)
		outString = outString + "\"(Personal Message)\"";
	else if (messageType == 5 || messageType == 7)
		try {
			//bodyBig
			/* should not have to sanitize if data pull is not truncacted
			if (!bodyBig.contains("</text></fquestion></formcontent></form></pnetmessage>"))
			{
				bodyBig = bodyBig + "</text></fquestion></formcontent></form></pnetmessage>";
				bodyBig = bodyBig.replace("</</", "</").replace("</t</", "</").replace("</te</", "</").replace("</tex</", "</").replace("</text</", "</");
				bodyBig = bodyBig.replace("<</", "</").replace("</text></text>", "</text>");
			}*/
			bodyBig = bodyBig.replace("NULL", " ");
			String parsedMessage=parseFormMessage(bodyBig);
			if (bodySmall.length() > parsedMessage.length())
			{
				System.out.println("Using BodySmall due to from message body small being larger than bodybig, line "+inputLines+", bbig content: ");
				System.out.println(parsedMessage);
				parsedMessage=bodySmall;
			}
			outString = (outString + "\"" + parsedMessage + "\"").replace("\"\"", "\"");
					
		} catch (SAXException e) {
			System.out.println("Using BodySmall due to XML tag mismatch, line "+inputLines+", XML content:");
			System.out.println(bodyBig);
			System.out.println(e.toString());
			outString = (outString + "\"" + bodySmall + "\"").replace("\"\"", "\"");
		} catch (IOException e1) {
			System.out.println("Using BodySmall due to XML tag mismatch, line "+inputLines+", XML content:");
			System.out.println(bodyBig);
			System.out.println(e1.toString());
			outString = (outString + "\"" + bodySmall + "\"").replace("\"\"", "\"");
		}
	else
		outString = outString + "\"" + bodyBig + "\"";

	return outString;
}

static String parseMessage(String body) throws SAXException, IOException {
	String outString="";
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    Document xmlBody;
    String bodyFixed = body.replace("\"", "").replace("&#10;", " ").replace("&#13;", " ").replace("&#", " ")
    		.replace("&"," ").replace('\0', ' ').replace((char) 0x11,' ').replace((char) 0x13,' ').replace((char) 0x2,' ')
    		.replace((char) 0x1,' ').replace((char) 0x15,' ');
	DocumentBuilder builder = null;
	try {
		builder = factory.newDocumentBuilder();
	} catch (ParserConfigurationException e) {
		//e.printStackTrace();
		return "ERROR";
	}
	InputSource is = new InputSource(new StringReader(bodyFixed));
	xmlBody = builder.parse(is);
    NodeList nodes = xmlBody.getElementsByTagName("pnetmessage");
    Element element = (Element) nodes.item(0);
    NodeList msgContent = element.getElementsByTagName("msg_content");
    
    outString =  getCharacterDataFromElement((Element) msgContent.item(0)).trim();
 return outString; 
 
 }
static String parseFormMessage(String body) throws SAXException, IOException {
	String outString="";
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    Document xmlBody;
		DocumentBuilder builder = null;
		try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			return "ERROR";
		}
	InputSource is = new InputSource(new StringReader(body.replace("\"", "").replace("&#10;", " ").replace("&#13;", " ").replace("&#", " ").replace("&"," ")));
	xmlBody = builder.parse(is);
	NodeList nodes = xmlBody.getElementsByTagName("pnetmessage");
	Element element = (Element) nodes.item(0);
	
//parse xml

    NodeList id = element.getElementsByTagName("id");
    NodeList name = element.getElementsByTagName("name");
    outString =  getCharacterDataFromElement((Element) id.item(0)) + " | "
    		   + getCharacterDataFromElement((Element) name.item(0));
    		   
	NodeList fquestion = element.getElementsByTagName("fquestion");
	for (int i=0; i < fquestion.getLength(); i++) {
		Element fqElement = (Element) fquestion.item(i);
	    NodeList fqText = fqElement.getElementsByTagName("fqtext"); //always have this
	    NodeList text = fqElement.getElementsByTagName("text"); //sometimes have this
	    NodeList px_odo = fqElement.getElementsByTagName("performx_odometer"); //sometimes have this
	    NodeList gps_odo = fqElement.getElementsByTagName("gps_odometer"); //sometimes have this
	    NodeList auto_odo = fqElement.getElementsByTagName("auto_odometer"); //sometimes have this
		//gps_odometer
		//auto_odometer
 	   outString = outString + " | " + getCharacterDataFromElement((Element) fqText.item(0)).replace("; ", ", ") + " | ";
 	   if (text.getLength()!=0) 
 		   outString = outString + getCharacterDataFromElement((Element) text.item(0)).replace("; ", ", ");
 	   if (px_odo.getLength()!=0)
 	   		outString = outString + "PerformX: " + getCharacterDataFromElement((Element) px_odo.item(0)).replace("; ", ", ") + " ";
 	  if (gps_odo.getLength()!=0)
	   		outString = outString + "GPS: " + getCharacterDataFromElement((Element) gps_odo.item(0)).replace("; ", ", ") + " ";
 	  if (auto_odo.getLength()!=0)
	   		outString = outString + "Auto: " + getCharacterDataFromElement((Element) gps_odo.item(0)).replace("; ", ", ") + " ";
	  
	}
    return outString.trim(); 
 
 }

 	/* this code will do a lookup for vid
 	 * Ideally we would only look up each vid once and store the results for future lookups so we
 	 * don't have to query the DB each time
 	 *  */
	static String lookupVehicle (int vid)
	{
 	String vehicle = vehicleVidLookup.get(vid);
 	String deletedVehicle;
 	String dsn;
	if (vehicle!=null) //if we have already looked up the vehicle, return the previously looked up value
	{
		return vehicle;
	}
	vehicle = String.valueOf(vid);
	 try {
	 	Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
	 	
	 	Connection conn = DriverManager.getConnection("jdbc:sqlserver://serv100;databaseName=intouch_main;integratedSecurity=true");
	    Statement stmt = conn.createStatement();
	    ResultSet rs;
	     
	    rs = stmt.executeQuery("SELECT trucknum,deleted_trucknum,dsn FROM dbo.v_unit WHERE vid="+ vid);
	              
	    while ( rs.next() ) {
	    	vehicle = rs.getString("trucknum");
	    	deletedVehicle = rs.getString("deleted_trucknum");
	    	dsn = rs.getString("dsn");
	    	//check for data accuracy
			if(vehicle==null && deletedVehicle==null && dsn==null)// invalid trucknum, invalid deleted num, invalid dsn, vid is only choice to use
		      		vehicle="VID: " + vid;	
			else if (vehicle==null && deletedVehicle==null ) // invalid trucknum, invalid deleted num, valid DSN
					vehicle="DSN: " + dsn;
			else if (vehicle==null) // invalid trucknum, valid deleted num
				vehicle=deletedVehicle + " (deleted)";
		 // valid trucknum
	  		vehicleVidLookup.put(vid, vehicle);
    }
    conn.close();
    
     } catch (Exception e) {
         System.out.println("Exception looking up truck number from serv100: ");
         System.out.println(e.toString());
        // System.out.println(e.getCause());
     }
 	 return vehicle;
 	}

 	/* this code will do a lookup for uid
 	 * Ideally we would only look up each uid once and store the results for future lookups so we
 	 * don't have to query the DB each time
 	 *  */
 	static String lookupUser (int uid)
 	{
     	String user = userUidLookup.get(uid);
 		if (user!=null)
 			return user;
 		else
 			user = String.valueOf(uid);
 	 try {
     	
     	Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
     	
     	Connection conn = DriverManager.getConnection("jdbc:sqlserver://serv100;databaseName=intouch_main;integratedSecurity=true");
        Statement stmt = conn.createStatement();
        ResultSet rs;
         
        rs = stmt.executeQuery("SELECT username FROM dbo.users WHERE uid="+ uid);
                  
        while ( rs.next() ) {
        	user = rs.getString("username");
        	userUidLookup.put(uid, user);
        }
        conn.close();
     	
     } catch (Exception e) {
         System.out.println("Exception looking up user from serv100: ");
         System.out.println(e.toString());
         //System.out.println(e.getCause());
     }
 	 return user;
 	}

 	public static String getCharacterDataFromElement(Element e) {
 		if (e == null)
 			return " ";
 	    Node child = e.getFirstChild();
 	    if (child instanceof CharacterData) {
 	       CharacterData cd = (CharacterData) child;
 	       return cd.getData();
 	    }
 	    return " ";
 	  }
 	private String sanitizeOutput(String inString) {
 		return 
 				inString.replace("amp;", "&").replace("&#10", "")
 				.replace("&apos;", "'").replace(" apos;", "'").replace("apos;", "'");
 	}
/*
public static void main (String[] args) {
   messageParserQuery myMessageParser = new messageParserQuery("G:\\My Drive\\Backup\\Documents\\weird.txt");
   myMessageParser.parseData();
}*/

}