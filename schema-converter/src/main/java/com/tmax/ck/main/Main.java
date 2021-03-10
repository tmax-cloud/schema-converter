package com.tmax.ck.main;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class Main {
	static List<List<String>> keySheet = new ArrayList<List<String>>();
	static Integer sequence = new Integer(0);
	static Map<String, JsonObject> schemaMap = new HashMap<String, JsonObject>();
	static Gson gsonObj = new Gson();

	public static void main(String args[]) {
		
		System.out.println("Start");
		try {
			String rootDir = "C:\\schema\\";
			String outputDir = rootDir + System.currentTimeMillis() + "\\";

			File rootDirFile = new File(rootDir);
			if (!rootDirFile.isDirectory()) {
				System.out.println(rootDir + " is not directory");
				return;
			}

			File outputDirFile = new File(outputDir);
			if (!outputDirFile.exists()) {
				outputDirFile.mkdir();
			}

			File[] jsonFiles = rootDirFile.listFiles(new FileFilter() {

				@Override
				public boolean accept(File pathname) {
					if (pathname.getAbsolutePath().endsWith(".json"))
						return true;
					else
						return false;
				}
			});

			for (File jsonFile : jsonFiles) {
				FileReader fr = new FileReader(jsonFile);
				schemaMap.put(jsonFile.getName(), gsonObj.fromJson(fr, JsonObject.class));
			}

			for (String schemaKey : schemaMap.keySet()) {
				JsonObject schema = schemaMap.get(schemaKey);
				convertDescriptionToCode(schema);
			}

			for (String schemaKey : schemaMap.keySet()) {
				JsonObject schema = schemaMap.get(schemaKey);
				
				FileWriter fw = new FileWriter(new File(outputDir + schemaKey));
				PrintWriter pw = new PrintWriter(fw);
				pw.print(schema.toString());
				pw.close();
			}
			
			int i = 0;
			HSSFWorkbook workbook = new HSSFWorkbook();
			HSSFSheet sheet = workbook.createSheet();
			for (List<String> pair : keySheet) {
				HSSFRow row = sheet.createRow(i++);
				HSSFCell cell1 = row.createCell(0);
				HSSFCell cell2 = row.createCell(1);
				cell1.setCellValue(pair.get(0));
				cell2.setCellValue(pair.get(1));
			}
			workbook.write(new File(outputDir + "output.xls"));
			workbook.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("End");
	}

	static void convertDescriptionToCode(JsonObject schema) {
		for (String key : schema.keySet()) {
			if (schema.get(key).isJsonObject()) {
				convertDescriptionToCode((JsonObject) schema.get(key));
			} else if (key.equals("description")) {
				String original = schema.get(key).getAsString();
				String code = "%STR" + String.valueOf(sequence++) + "";
				List<String> codePair = new ArrayList<String>();
				codePair.add(code);
				codePair.add(original);
				keySheet.add(codePair);
				schema.addProperty(key, code);
			}
		}
	}
}
