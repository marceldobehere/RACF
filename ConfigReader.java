package com.marcel.RACF;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.marcel.RACF.ConfigErrors.*;

import static com.marcel.RACF.Util.*;

public class ConfigReader {

	public static List<ConfigTokens.ConfigObject> ReadConfigFile(String filename) throws Exception
	{

		Path path = Paths.get(filename);

		byte[] binData;
		
		try {

			binData = Files.readAllBytes(path);

		} catch (IOException exception) {

			puts("File Not Found!");
			return null;

		}

		String strData = new String(binData, StandardCharsets.UTF_8);

		return ReadConfigString(strData);
	}

	private static ConfigTokens.ConfigObject FindObject(String name, List<ConfigTokens.ConfigObject> objectList)
	{
		for (ConfigTokens.ConfigObject obj : objectList)
		{
			if (obj instanceof ConfigTokens.ConfigLabelObject)
			{
				if (((ConfigTokens.ConfigLabelObject) obj).label.equals(name))
					return obj;
			}
			else if (obj instanceof ConfigTokens.ConfigVariableObject)
			{
				if (((ConfigTokens.ConfigVariableObject) obj).name.equals(name))
					return obj;
			}
		}

		return null;
	}

	private static ConfigTokens.ConfigObject FindObject(String name, ConfigTokens.ConfigObject[] objectList)
	{
		//puts("Finding \"" + name + "\" in <" + objectList + ">");
		for (ConfigTokens.ConfigObject obj : objectList)
		{
			if (obj instanceof ConfigTokens.ConfigLabelObject)
			{
				if (((ConfigTokens.ConfigLabelObject) obj).label.equals(name))
					return obj;
			}
			else if (obj instanceof ConfigTokens.ConfigVariableObject)
			{
				if (((ConfigTokens.ConfigVariableObject) obj).name.equals(name))
					return obj;
			}
		}

		return null;
	}



	private static ConfigTokens.ConfigVariableObject GetVarFromDictUsingName(String name, ConfigTokens.ConfigVariableDictionary dict)
	{
		for (ConfigTokens.ConfigVariableObject obj : dict.values)
			if (obj.name.equals(name))
				return obj;

		return null;
	}


	public static ConfigTokens.ConfigVariableObjectType GetValue(String configPath, List<ConfigTokens.ConfigObject> objectList, boolean resolveReference) throws Exception {

		ConfigTokens.ConfigObject object = new ConfigTokens.ConfigLabelObject("", objectList.toArray(new ConfigTokens.ConfigObject[0]));

		//puts("AAAA: " + configPath);

		StringBuilder temp = new StringBuilder();
		for (int i = 0; i < configPath.length(); i++)
		{
			char chr = configPath.charAt(i);
			if (chr == '.')
			{
				//puts("B: " + temp);
				object = FindObject(temp.toString(), ((ConfigTokens.ConfigLabelObject)object).objects);
				temp = new StringBuilder();
			}
			else if (chr == '[')
			{
				if (temp.length() > 0)
				{
					object = FindObject(temp.toString(), ((ConfigTokens.ConfigLabelObject)object).objects);
					temp = new StringBuilder();
				}
				i++;
				int start = i;
				int layer = 1;
				while (true)
				{
					if (i >= configPath.length())
						throw new UnterminatedLabelHeadException("] was not found!", GetContext(configPath, i-1));

					chr = configPath.charAt(i);

					if (chr == '[')
						layer++;
					else if (chr == ']')
						layer--;
					if (layer == 0)
						break;
					i++;
				}

				String data = configPath.substring(start, i);

				while (data.startsWith("$"))
					data = GetValue(data.substring(1), objectList, true).genericToString();

				
				if (isInteger(data))
				{
					//puts("TYPE:" + object.toString());
					int index = Integer.parseInt(data);
					if (object instanceof ConfigTokens.ConfigVariableObject)
					{
						if (((ConfigTokens.ConfigVariableObject) object).variable instanceof ConfigTokens.ConfigVariableArray arr)
						{

							if (index < 0 || index >= arr.values.length)
								throw new Exception("Index was out of Bounds!");

							object = arr.values[index];
						}
						else
						{
							throw  new Exception("Object is not an Array!");
						}
					}
					else if (object instanceof ConfigTokens.ConfigVariableArray arr)
					{

						if (index < 0 || index >= arr.values.length)
							throw new Exception("Index was out of Bounds!");

						object = arr.values[index];
					}
					else
					{
						throw  new Exception("Object is not an Variable");
					}
				}
				else
				{
					if (object instanceof ConfigTokens.ConfigVariableObject)
					{
						if (((ConfigTokens.ConfigVariableObject)object).variable instanceof ConfigTokens.ConfigVariableDictionary dict)
						{
							ConfigTokens.ConfigVariableObject obj = GetVarFromDictUsingName(data, dict);

							if (obj == null)
								throw new Exception("Variable wasn't found!");

							object = obj;
						}
						else
						{
							throw  new Exception("Object is not a Dictionary!");
						}
					}
					else if (object instanceof ConfigTokens.ConfigVariableDictionary dict)
					{
						ConfigTokens.ConfigVariableObject obj = GetVarFromDictUsingName(data, dict);

						if (obj == null)
							throw new Exception("Variable wasn't found!");

						object = obj;
					}
					else
					{
						throw  new Exception("Object is not an Variable");
					}
				}
			}
			else
				temp.append(chr);
		}

		//puts("AA: " + temp);

		if (temp.length() > 0)
			object = FindObject(temp.toString(), ((ConfigTokens.ConfigLabelObject)object).objects);

		if (object instanceof ConfigTokens.ConfigVariableObject)
			object = ((ConfigTokens.ConfigVariableObject)object).variable;

		return (ConfigTokens.ConfigVariableObjectType) object;



		/*String[] parts = configPath.split(java.util.regex.Pattern.quote("."));



		ConfigLabelObject currentLabel = FindLabel(parts[0], objectList);

		for (int i = 0; i < parts.length - 2; i++)
			currentLabel = FindLabel(parts[i+1], currentLabel.objects);

		ConfigVariableObjectType var = null;

		for (ConfigObject obj : currentLabel.objects) {
			if (obj instanceof ConfigVariableObject)
				if (((ConfigVariableObject)obj).name.equals(parts[parts.length - 1]))
					var = ((ConfigVariableObject)obj).variable;
		}

		if (resolveReference)
			if (var != null)
				if (var instanceof ConfigVariableReference)
					var = GetValue((((ConfigVariableReference)var).variableName), objectList, true);

		// Potentially add indexing!
		return var;*/
	}

	private static String GetContext(String data, int cursorPosition)
	{
		if (data.length() == 0)
			return "";

		int start = cursorPosition;
		int end = cursorPosition;

		if (start < 0)
			start = 0;

		if (end >= data.length())
			end = data.length();

		int counter = 50;

		while (start > 0 && counter > 0)
		{
			if (data.charAt(start) == '\n')
				break;

			start--;

			counter--;
		}

		counter = 50;

		while (end < data.length() && counter > 0)
		{
			if (data.charAt(end) == '\n')
				break;

			end++;

			counter--;
		}

		if (data.charAt(start) == '\n')
			start++;

		String part1 = data.substring(start, end);

		int tabCount = 0;

		for (byte t : part1.getBytes())
			if (t == '\t')
				tabCount++;

		String part2 = " ".repeat((cursorPosition - start)-tabCount) + "\t".repeat(tabCount) + "^ Error";
		String part3 = "-".repeat((end - start)-tabCount) + "----".repeat(tabCount);

		return "\n" + part3 + "\n" + part1 + "\n" + part2 + "\n" + part3 ;
	}

	private static int CheckComments(String data, int cursorPosition, boolean skipString) throws Exception {

		char current = data.charAt(cursorPosition);

		if (current == '(') {
			if (data.indexOf(')', cursorPosition) == -1)
				throw new UnterminatedCommentException("( Comment was not closed!", GetContext(data, cursorPosition));

			cursorPosition = data.indexOf(')', cursorPosition);

			cursorPosition++;

			return cursorPosition;
		}

		if (current == '\"' && skipString)
		{
			current = ' ';

			cursorPosition++;

			int tempPos = cursorPosition;

			while (current != '\"' && cursorPosition < data.length())
			{
				current = data.charAt(cursorPosition);

				if (current == '\\')
					cursorPosition++;

				cursorPosition++;
			}

			if (cursorPosition >= data.length())
				throw new UnterminatedStringException("String was not terminated", GetContext(data, tempPos));

			return cursorPosition;
		}

		if (current == '/') {

			if (data.charAt(cursorPosition + 1) == '/') {

				cursorPosition = data.indexOf('\n', cursorPosition);
				cursorPosition++;
				return cursorPosition;

			} else if (data.charAt(cursorPosition + 1) == '*') {

				int starPosition = data.indexOf("*/", cursorPosition + 2);

				if (starPosition != -1) {
					cursorPosition = starPosition + 1;
					cursorPosition++;
					return cursorPosition;
				} else
					throw new UnterminatedCommentException("/* Comment was not closed!", GetContext(data, cursorPosition));

			}

		}

		return cursorPosition;
	}

	private static final String validVarChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789-äöü";

	public static List<ConfigTokens.ConfigObject> ReadConfigString(String data) throws Exception
	{

		List<ConfigTokens.ConfigObject> objectList = new ArrayList<>();

		int contentSize = data.length();
		int cursorPosition = 0;

		//CT. new ConfigLabelObject("Test_Label_2", __);

		//('[', '/', '(')

		while (cursorPosition < contentSize) {

			char current = data.charAt(cursorPosition);

			{

				int tempCursorPosition = CheckComments(data, cursorPosition, true);

				if (cursorPosition != tempCursorPosition)
				{
					cursorPosition = tempCursorPosition;
					continue;
				}

			}


			if (current == '[') {

				int labelEndPosition = data.indexOf(']', cursorPosition);

				boolean isError = labelEndPosition == -1 || (
					labelEndPosition > data.indexOf('[', cursorPosition + 1) &&
					data.indexOf('[', cursorPosition + 1) != -1
				);

				if (isError) {
					throw new UnterminatedLabelHeadException(
						"] was not found!", GetContext(data, cursorPosition)
					);
				}

				String label = data.substring(
					cursorPosition + 1, labelEndPosition
				);

				cursorPosition = labelEndPosition + 1;

				int layer = 0, startBracket = 0, endBracket = 0;

				while (cursorPosition < contentSize)
				{
					current = data.charAt(cursorPosition);

					if (current == '{')
					{
						layer++;

						if (layer == 1)
							startBracket = cursorPosition;

						cursorPosition++;

						continue;
					}

					if (current == '}')
					{
						layer--;

						if (layer == 0)
						{
							endBracket = cursorPosition;
							break;
						}

						cursorPosition++;

						continue;
					}

					{
						int tempCursorPosition = CheckComments(data, cursorPosition, true);

						if (cursorPosition != tempCursorPosition)
						{
							cursorPosition = tempCursorPosition;
							continue;
						}
					}

					cursorPosition++;

				}

				if (cursorPosition == data.length())
					throw new UnterminatedLabelBodyException("} was not found!", GetContext(data, labelEndPosition));

				//puts("LABEL: \""+ label + "\"");
				//puts("BRACKET DATA:\n<" + data.substring(startBracket + 1, endBracket) + ">\n");

				List<ConfigTokens.ConfigObject> tempList = ReadConfigString(data.substring(startBracket + 1, endBracket));
				ConfigTokens.ConfigLabelObject labelObject = new ConfigTokens.ConfigLabelObject(label, tempList.toArray(new ConfigTokens.ConfigObject[0]));

				objectList.add(labelObject);

				continue;
			}

			if (current == '=')
			{
				int equalSignPosition = cursorPosition;

				cursorPosition--;

				while (validVarChars.indexOf(data.charAt(cursorPosition)) == -1)
					cursorPosition--;

				int varNameEnd = cursorPosition;

				while (validVarChars.indexOf(data.charAt(cursorPosition)) != -1)
					cursorPosition--;

				int varNameStart = cursorPosition+1;

				String varName = data.substring(varNameStart, varNameEnd+1);

				//puts("VARNAME: " + varName);

				cursorPosition = equalSignPosition;

				cursorPosition++;

				if (cursorPosition >= data.length())
					throw new MissingValueException("Missing value for \"" + varName + "\"!", GetContext(data, equalSignPosition));

				while (validVarChars.indexOf(data.charAt(cursorPosition)) == -1 && "\"${[".indexOf(data.charAt(cursorPosition)) == -1)
				{
					cursorPosition++;
					if (cursorPosition >= data.length())
						throw new MissingValueException("Missing value for \"" + varName + "\"!", GetContext(data, equalSignPosition));
				}

				int valueStart = cursorPosition;

				char valueChar = data.charAt(valueStart);

				//puts("VAL CHAR: " + valueChar);

				if (valueChar == '\"')
				{
					// String
					current = ' ';

					cursorPosition++;

					while (current != '\"')
					{
						current = data.charAt(cursorPosition);

						if (current == '\\')
							cursorPosition++;

						cursorPosition++;
					}

					int valueEnd = cursorPosition;

					String value = data.substring(valueStart+1, valueEnd-1); // get string data

					//puts("VALUE: \"" + value + "\"");

					ConfigTokens.ConfigVariableObject obj = new ConfigTokens.ConfigVariableObject( // make new VariableObject with a VariableStringObject with the variable data
						varName,
						new ConfigTokens.ConfigVariableString(value)
					);

					objectList.add(obj); // add the object to the actual ObjectThing List

					continue;
				}
				else if ("-.0123456789".indexOf(valueChar) != -1)
				{
					// Probably Int or Double
					cursorPosition++;

					current = '0';

					while (".0123456789".indexOf(current) != -1)
					{
						current = data.charAt(cursorPosition);

						if (current == '-')
							throw new InvalidNumberException("Minus Symbol can't be mid Number.", GetContext(data, cursorPosition));

						cursorPosition++;
					}

					int valueEnd = cursorPosition;

					String value = data.substring(valueStart, valueEnd-1); // get string data

					//puts("VALUE: \"" + value + "\"");

					ConfigTokens.ConfigVariableObject obj;

					if (value.contains("."))
					{
						obj = new ConfigTokens.ConfigVariableObject( // make new VariableObject with a VariableStringObject with the variable data
							varName,
							new ConfigTokens.ConfigVariableDouble(Double.parseDouble(value))
						);
					}
					else
					{
						obj = new ConfigTokens.ConfigVariableObject( // make new VariableObject with a VariableStringObject with the variable data
							varName,
							new ConfigTokens.ConfigVariableInteger(Integer.parseInt(value))
						);
					}

					objectList.add(obj); // add the object to the actual ObjectThing List

					continue;
				}
				else if (valueChar == '$')
				{
					cursorPosition++;

					while (validVarChars.indexOf(data.charAt(cursorPosition)) != -1 || data.charAt(cursorPosition) == '.')
						cursorPosition++;

					int valueEnd = cursorPosition;

					String value = data.substring(valueStart+1, valueEnd); // get string data

					//puts("VALUE: \"" + value + "\"");

					ConfigTokens.ConfigVariableObject obj = new ConfigTokens.ConfigVariableObject( // make new VariableObject with a VariableStringObject with the variable data
						varName, new ConfigTokens.ConfigVariableReference(value)
					);

					objectList.add(obj); // add the object to the actual ObjectThing List

					continue;
				}
				else if (valueChar == '[')
				{
					int layer = 0, startBracket = 0, endBracket = 0;

					while (cursorPosition < contentSize)
					{
						current = data.charAt(cursorPosition);

						if (current == '[')
						{
							layer++;

							if (layer == 1)
								startBracket = cursorPosition;

							cursorPosition++;

							continue;
						}

						if (current == ']')
						{
							layer--;

							if (layer == 0)
							{
								endBracket = cursorPosition;
								break;
							}

							cursorPosition++;

							continue;
						}

						{
							int tempCursorPosition = CheckComments(data, cursorPosition, true);

							if (cursorPosition != tempCursorPosition)
							{
								cursorPosition = tempCursorPosition;
								continue;
							}
						}

						cursorPosition++;
					}

					if (cursorPosition >= contentSize)
						throw new UnterminatedArrayException("] was not found!", GetContext(data, startBracket));

					//puts("BRACKET DATA:\n<" + data.substring(startBracket + 1, endBracket) + ">\n");

					List<ConfigTokens.ConfigVariableObjectType> tempList = ReadConfigStringArray(" " + data.substring(startBracket + 1, endBracket) + " ");

					//ConfigLabelObject labelObject = new ConfigLabelObject(label, tempList.toArray(new ConfigObject[0]));

					ConfigTokens.ConfigVariableObject obj = new ConfigTokens.ConfigVariableObject(
						varName, new ConfigTokens.ConfigVariableArray(tempList.toArray(new ConfigTokens.ConfigVariableObjectType[0]))
					);

					objectList.add(obj);

					continue;
				}
				else if (valueChar == '{')
				{
					// Dictionary
					// we can pass the content between the {} into the function again like in the label thing

					int layer = 0, startBracket = 0, endBracket = 0;

					while (cursorPosition < contentSize)
					{
						current = data.charAt(cursorPosition);

						if (current == '{')
						{
							layer++;

							if (layer == 1)
								startBracket = cursorPosition;

							cursorPosition++;

							continue;
						}

						if (current == '}')
						{
							layer--;

							if (layer == 0)
							{
								endBracket = cursorPosition;
								break;
							}

							cursorPosition++;

							continue;
						}

						{
							int tempCursorPosition = CheckComments(data, cursorPosition, true);

							if (cursorPosition != tempCursorPosition)
							{
								cursorPosition = tempCursorPosition;
								continue;
							}

						}

						cursorPosition++;

					}

					if (cursorPosition >= contentSize)
							throw new UnterminatedDictionaryException("} was not found!", GetContext(data, startBracket));

					//puts("BRACKET DATA:\n<" + data.substring(startBracket + 1, endBracket) + ">\n");

					List<ConfigTokens.ConfigObject> tempList = ReadConfigString(" " + data.substring(startBracket + 1, endBracket) + " ");
					//ConfigLabelObject labelObject = new ConfigLabelObject(label, tempList.toArray(new ConfigObject[0]));

					List<ConfigTokens.ConfigVariableObject> vars = new ArrayList<>();
					for (ConfigTokens.ConfigObject obj_ : tempList)
					{
						if (obj_ instanceof ConfigTokens.ConfigVariableObject)
							vars.add((ConfigTokens.ConfigVariableObject) obj_);
					}

					ConfigTokens.ConfigVariableObject obj = new ConfigTokens.ConfigVariableObject(
						varName,
						new ConfigTokens.ConfigVariableDictionary(vars)
					);

					objectList.add(obj);

					continue;

				}
				else
				{
					cursorPosition++;

					current = data.charAt(cursorPosition);

					while ("truefals".indexOf(current) != -1)
					{
						current = data.charAt(cursorPosition);
						cursorPosition++;
					}

					int valueEnd = cursorPosition;

					String value = data.substring(valueStart, valueEnd - 1); // get string data
					//puts("VALUE: \"" + value + "\"");

					ConfigTokens.ConfigVariableObject obj;

					// get the word like the varname

					if (value.equals("true"))
					{
						obj = new ConfigTokens.ConfigVariableObject( // make new VariableObject with a VariableStringObject with the variable data
							varName, new ConfigTokens.ConfigVariableBoolean(true)
						);
					}
					else if (value.equals("false"))
					{
						obj = new ConfigTokens.ConfigVariableObject( // make new VariableObject with a VariableStringObject with the variable data
							varName, new ConfigTokens.ConfigVariableBoolean(false)
						);
					}
					else
					{
						throw new InvalidBooleanException("\"" + value + "\" is not a boolean!", GetContext(data, valueStart));
					}

					objectList.add(obj); // add the object to the actual ObjectThing List

					continue;
				}
			}

			cursorPosition++;

		}

		return objectList;
}

	public static List<ConfigTokens.ConfigVariableObjectType> ReadConfigStringArray(String data) throws Exception {

		List<ConfigTokens.ConfigVariableObjectType> objectList = new ArrayList<>();

		int contentSize = data.length();
		int cursorPosition = 0;

		while (cursorPosition < contentSize) {

			char current;

			{

				int tempCursorPosition = CheckComments(data, cursorPosition, false);

				if (cursorPosition != tempCursorPosition)
				{
					cursorPosition = tempCursorPosition;
					continue;
				}

			}

			if (validVarChars.indexOf(data.charAt(cursorPosition)) != -1 || "\"${[".indexOf(data.charAt(cursorPosition)) != -1)
			{
				//while (validVarChars.indexOf(data.charAt(cursorPosition)) == -1 && "\"${[".indexOf(data.charAt(cursorPosition)) == -1)
					//cursorPosition++;

				int valueStart = cursorPosition;

				char valueChar = data.charAt(valueStart);

				//puts("VAL CHAR: " + valueChar);

				if (valueChar == '\"')
				{
					// String
					current = ' ';

					cursorPosition++;

					while (current != '\"')
					{
						current = data.charAt(cursorPosition);

						if (current == '\\')
							cursorPosition++;

						cursorPosition++;
					}

					int valueEnd = cursorPosition;

					String value = data.substring(valueStart + 1, valueEnd - 1); // get string data

					//puts("VALUE: \"" + value + "\"");

					objectList.add(new ConfigTokens.ConfigVariableString(value)); // add the object to the actual ObjectThing List

					continue;
				}
				else if ("-.0123456789".indexOf(valueChar) != -1)
				{
					// Probably Int or Double
					cursorPosition++;

					current = '0';

					while (".0123456789".indexOf(current) != -1)
					{
						current = data.charAt(cursorPosition);

						if (current == '-')
							throw new InvalidNumberException("Minus Symbol can't be mid Number.", GetContext(data, cursorPosition));

						cursorPosition++;
					}

					int valueEnd = cursorPosition;

					String value = data.substring(valueStart, valueEnd - 1); // get string data

					//puts("VALUE: \"" + value + "\"");

					if (value.contains("."))
						objectList.add(new ConfigTokens.ConfigVariableDouble(Double.parseDouble(value)));
					else
						objectList.add(new ConfigTokens.ConfigVariableInteger(Integer.parseInt(value)));

					 // add the object to the actual ObjectThing List

					continue;
				}
				else if (valueChar == '$')
				{
					cursorPosition++;

					while (validVarChars.indexOf(data.charAt(cursorPosition)) != -1 || data.charAt(cursorPosition) == '.')
						cursorPosition++;

					int valueEnd = cursorPosition;

					String value = data.substring(valueStart + 1, valueEnd); // get string data

					//puts("VALUE: \"" + value + "\"");

					objectList.add(new ConfigTokens.ConfigVariableReference(value)); // add the object to the actual ObjectThing List

					continue;
				}
				else if (valueChar == '[')
				{
					int layer = 0, startBracket = 0, endBracket = 0;

					while (cursorPosition < contentSize)
					{

						current = data.charAt(cursorPosition);

						if (current == '[')
						{

							layer++;

							if (layer == 1)
								startBracket = cursorPosition;

							cursorPosition++;

							continue;

						}

						if (current == ']')
						{

							layer--;

							if (layer == 0)
							{
								endBracket = cursorPosition;
								break;
							}

							cursorPosition++;

							continue;

						}

						{

							int tempCursorPosition = CheckComments(data, cursorPosition, true);

							if (cursorPosition != tempCursorPosition)
							{
								cursorPosition = tempCursorPosition;
								continue;
							}

						}

						cursorPosition++;

					}

					if (cursorPosition >= contentSize)
						throw new UnterminatedArrayException("] was not found!", GetContext(data, startBracket));

					//puts("BRACKET DATA:\n<" + data.substring(startBracket + 1, endBracket) + ">\n");

					List<ConfigTokens.ConfigVariableObjectType> tempList = ReadConfigStringArray(" " + data.substring(startBracket + 1, endBracket) + " ");

					//ConfigLabelObject labelObject = new ConfigLabelObject(label, tempList.toArray(new ConfigObject[0]));

					objectList.add(new ConfigTokens.ConfigVariableArray(tempList.toArray(new ConfigTokens.ConfigVariableObjectType[0])));

					continue;
				}
				else if (valueChar == '{')
				{
					// Dictionary
					// we can pass the content between the {} into the function again like in the label thing

					int layer = 0, startBracket = 0, endBracket = 0;

					while (cursorPosition < contentSize)
					{
						current = data.charAt(cursorPosition);

						if (current == '{')
						{
							layer++;

							if (layer == 1)
								startBracket = cursorPosition;

							cursorPosition++;

							continue;
						}

						if (current == '}')
						{
							layer--;

							if (layer == 0)
							{
								endBracket = cursorPosition;
								break;
							}

							cursorPosition++;

							continue;
						}

						{

							int tempCursorPosition = CheckComments(data, cursorPosition, true);

							if (cursorPosition != tempCursorPosition)
							{
								cursorPosition = tempCursorPosition;
								continue;
							}

						}

						cursorPosition++;

					}

					if (cursorPosition >= contentSize)
						throw new UnterminatedDictionaryException("} was not found!", GetContext(data, startBracket));

					//puts("BRACKET DATA:\n<" + data.substring(startBracket + 1, endBracket) + ">\n");

					List<ConfigTokens.ConfigObject> tempList = ReadConfigString(" " + data.substring(startBracket + 1, endBracket) + " ");
					//ConfigLabelObject labelObject = new ConfigLabelObject(label, tempList.toArray(new ConfigObject[0]));

					List<ConfigTokens.ConfigVariableObject> vars = new ArrayList<>();

					for (ConfigTokens.ConfigObject obj_ : tempList)
					{
						if (obj_ instanceof ConfigTokens.ConfigVariableObject)
							vars.add((ConfigTokens.ConfigVariableObject) obj_);
					}

					objectList.add(new ConfigTokens.ConfigVariableDictionary(vars));

					continue;

				}
				else
				{
					cursorPosition++;

					current = data.charAt(cursorPosition);

					while ("truefals".indexOf(current) != -1)
					{
						current = data.charAt(cursorPosition);
						cursorPosition++;
					}

					int valueEnd = cursorPosition;

					String value = data.substring(valueStart, valueEnd-1); // get string data

					//puts("VALUE: \"" + value + "\"");

					// get the word like the varname

					if (value.equals("true"))
						objectList.add(new ConfigTokens.ConfigVariableBoolean(true));

					else if (value.equals("false"))
						objectList.add(new ConfigTokens.ConfigVariableBoolean(false));

					else
					{
						throw new InvalidBooleanException("\"" + value + "\" is not a boolean!", GetContext(data, valueStart));
					}

					// add the object to the actual ObjectThing List

					continue;
				}

			}

			cursorPosition++;

		}

		return objectList;

	}

	public static void WriteConfigFile(String filename, List<ConfigTokens.ConfigObject> objectList)
	{
		File yourFile = new File(filename);

		try {
			yourFile.createNewFile(); // if file already exists will do nothing
		} catch (IOException e) {
			e.printStackTrace();
		}

		try (PrintWriter out = new PrintWriter(filename)) {
			out.println(TokensToString(objectList));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void PrintTokens(List<ConfigTokens.ConfigObject> objectList)
	{
		for (int i = 0; i < objectList.size(); i++)
		{

			ConfigTokens.ConfigObject obj = objectList.get(i);

			if (obj instanceof ConfigTokens.ConfigLabelObject)
			{
				puts(PrintToken(obj, 0, i == 0));
			}

		}
	}

	public static String TokensToString(List<ConfigTokens.ConfigObject> objectList)
	{
		StringBuilder temp = new StringBuilder();

		for (int i = 0; i < objectList.size(); i++)
		{

			ConfigTokens.ConfigObject obj = objectList.get(i);

			if (obj instanceof ConfigTokens.ConfigLabelObject)
			{
				temp.append(PrintToken(obj, 0, i == 0)).append("\n");
			}

		}

		return temp.toString();
	}

	private static String PrintToken(ConfigTokens.ConfigObject obj, int indentlevel, boolean firstLabel)
	{
		StringBuilder temp = new StringBuilder();

		if (obj instanceof ConfigTokens.ConfigVariableObject)
		{
			temp.append(
				MessageFormat.format(
					"{0}{1} = {2}\n",
					"\t".repeat(indentlevel),
					((ConfigTokens.ConfigVariableObject) obj).name,
					((ConfigTokens.ConfigVariableObject) obj).variable
				)
			);
		}
		else if (obj instanceof ConfigTokens.ConfigLabelObject)
		{
			if (!firstLabel)
				temp.append("\n");

			temp.append(
				MessageFormat.format(
					"{0}[{1}]\n{0}'{'\n",
					"\t".repeat(indentlevel),
					((ConfigTokens.ConfigLabelObject) obj).label
				)
			);

			//for (ConfigObject currentObject : ((ConfigLabelObject) obj).objects)
			//PrintToken(currentObject, indentlevel + 1);

			boolean labelBefore = false;

			for (int i = 0; i < ((ConfigTokens.ConfigLabelObject) obj).objects.length; i++) {

				ConfigTokens.ConfigObject currentObject = ((ConfigTokens.ConfigLabelObject) obj).objects[i];

				if (currentObject instanceof ConfigTokens.ConfigVariableObject)

					if (labelBefore)
						temp.append("\n");

				temp.append(PrintToken(currentObject, indentlevel + 1, i == 0));

				labelBefore = currentObject instanceof ConfigTokens.ConfigLabelObject;
			}

			temp.append(
				MessageFormat.format(
					"{0}'}'\n",
					"\t".repeat(indentlevel)
				)
			);
		}

		return temp.toString();

	}

}
