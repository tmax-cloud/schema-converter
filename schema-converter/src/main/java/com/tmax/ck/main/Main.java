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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translate.TranslateOption;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class Main {
//	static List<List<String>> keySheet = new ArrayList<List<String>>();
	static Map<String, List<List<String>>> keySheetMap = new HashMap<String, List<List<String>>>();
	static Integer sequence = new Integer(0);
	static Map<String, JsonObject> schemaMap = new HashMap<String, JsonObject>();
	static Map<String, Map<String, Object>> yamlMap = new HashMap<String, Map<String, Object>>();
	static Gson gsonObj = new Gson();
	static ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
	static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10, 100, 0, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>());

	// configuration
	static boolean autoTranslation = false;

	public static void main(String args[]) {

		System.out.println("Start");
		try {
			Translate translate = null;
			if (autoTranslation) {
				translate = TranslateOptions.newBuilder().build().getService();
			}

			// schema 파일 혹은 CRD yaml 파일이 있는 root directory
//			String rootDir = "C:\\schema\\";
			String rootDir = "C:\\cicd-crd\\";
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

			// schema json 파일만 걸러냄
			File[] jsonFiles = rootDirFile.listFiles(new FileFilter() {

				@Override
				public boolean accept(File pathname) {
					if (pathname.getAbsolutePath().endsWith(".json"))
						return true;
					else
						return false;
				}
			});

			// CRD yaml 파일만 걸러냄
			File[] yamlFiles = rootDirFile.listFiles(new FileFilter() {

				@Override
				public boolean accept(File pathname) {
					if (pathname.getAbsolutePath().endsWith(".yaml"))
						return true;
					else
						return false;
				}
			});

			// 언마샬러가 달라서 json 과 yaml 로직 분리
			for (File jsonFile : jsonFiles) {
				FileReader fr = new FileReader(jsonFile);
				schemaMap.put(jsonFile.getName(), gsonObj.fromJson(fr, JsonObject.class));
			}

			for (File yamlFile : yamlFiles) {
				yamlMap.put(yamlFile.getName(), mapper.readValue(yamlFile, Map.class));
			}

			// 재귀함수로 파일별로 키매핑 및 keySheetMap 에서 통합관리 (key = 파일명 + json path, value = key로
			// 대체되기 전 원본 String)
			for (String schemaKey : schemaMap.keySet()) {
				JsonObject schema = schemaMap.get(schemaKey);
				List<List<String>> keySheet = new ArrayList<List<String>>();
				convertDescriptionToCode(keySheet, schema, schemaKey);
				keySheetMap.put(schemaKey, keySheet);
			}

			for (String yamlKey : yamlMap.keySet()) {
				Map<String, Object> yaml = yamlMap.get(yamlKey);
				List<List<String>> keySheet = new ArrayList<List<String>>();
				convertCrdDescriptionToCode(keySheet, yaml, yamlKey);
				keySheetMap.put(yamlKey, keySheet);
			}

			// key로 replace 된 후의 json schema 및 CRD yaml 에 대해서 output directory에 저장
			for (String schemaKey : schemaMap.keySet()) {
				JsonObject schema = schemaMap.get(schemaKey);

				FileWriter fw = new FileWriter(new File(outputDir + schemaKey));
				PrintWriter pw = new PrintWriter(fw);
				pw.print(schema.toString());
				pw.close();
			}

			for (String yamlKey : yamlMap.keySet()) {
				mapper.writeValue(new File(outputDir + yamlKey), yamlMap.get(yamlKey));
			}

			// batch 처리를 위해서 작성한 코드. 현재 payload가 너무 크면 400 에러가 발생하는 문제가 있어서 주석처리
//			List<Translation> translatedList = translate.translate(originalList,
//					TranslateOption.sourceLanguage("en").targetLanguage("ko"));

			// CSV에서 콤마가 separator로 인식됨, 이스케이프 처리 하기 싫어서 OOXML 이용
			// 단일 파일에
			HSSFWorkbook workbook = new HSSFWorkbook();

			for (String sheetKey : keySheetMap.keySet()) {
				int i = 0;
				// 파일별로 sheet를 분리하고
				HSSFSheet sheet = workbook.createSheet(sheetKey);

				for (List<String> pair : keySheetMap.get(sheetKey)) {
					// sheet에서 key/value 별로 row 생성
					HSSFRow row = sheet.createRow(i);

					// key값, 원본, 번역본 순
					HSSFCell cell1 = row.createCell(0);
					HSSFCell cell2 = row.createCell(1);
					HSSFCell cell3 = row.createCell(2);
					System.out.println(pair.get(0));
					cell1.setCellValue(pair.get(0));
					cell2.setCellValue(pair.get(1));
					// cell3.setCellValue(translatedList.get(i).getTranslatedText());

					if (autoTranslation) {
						String translated = translate
								.translate(pair.get(1), TranslateOption.sourceLanguage("en").targetLanguage("ko"))
								.getTranslatedText();
						cell3.setCellValue(translated);
						System.out.println(translated);
					}

					// row 처리를 위한 인덱스 변수 증가
					i++;
				}

			}

			// output directory 에 output.xls 파일로 엑셀파일 저장
			workbook.write(new File(outputDir + "output.xls"));
			workbook.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("End");
	}

	// value가 json object 일 경우 재귀로 하위탐색, key가 description일 경우 코드 변환
	static void convertDescriptionToCode(List<List<String>> keySheet, JsonObject schema, String path) {
		for (String key : schema.keySet()) {
			if (schema.get(key).isJsonObject()) {
				convertDescriptionToCode(keySheet, (JsonObject) schema.get(key), path + "." + key);
			} else if (key.equals("description")) {
				String original = schema.get(key).getAsString();
				String code = "%" + path;
				List<String> codePair = new ArrayList<String>();
				codePair.add(code);
				codePair.add(original);
				keySheet.add(codePair);
				schema.addProperty(key, code);
			}
		}
	}

	// value가 Map의 자식클래스일 경우 재귀로 하위탐색, key가 description일 경우 코드 변환
	static void convertCrdDescriptionToCode(List<List<String>> keySheet, Map<String, Object> yaml, String path) {
		for (String key : yaml.keySet()) {
			if (yaml.get(key) instanceof Map<?, ?>) {
				convertCrdDescriptionToCode(keySheet, (Map<String, Object>) yaml.get(key), path + "." + key);
			} 
			/*
			 * value가 List<?> 면서, 구성요소들이 Map<?, ?> 일 경우에 한정
			 * 구성요소들이 List<?> 일 경우 이슈 나오면 처리할 것임
			 */
			// value가 List 일 경우 예외처리(임시)
			else if (yaml.get(key) instanceof List<?>) { 
				for (Object obj : (List) yaml.get(key)) {
					if (obj instanceof Map<?, ?>) {
						convertCrdDescriptionToCode(keySheet, (Map<String, Object>) obj, path + "." + key);
					}
				}
			} 
			/*
			 * jsonSchema 가 CRD yaml 아래에는 openAPIV3Schema 로 들어가는데, openAPIV3Schema 아닌 곳에도 description을 key로 가지는 오브젝트 다수 발견
			 * 우선 스펙상 openAPIV3Schema 만 structural schema에 포함되기 때문에 한정 지음
			 * 비효율적인 코딩이지만 openAPIV3Schema 객체의 yaml path를 확정지을 수 없기 때문에 풀스캔
			 */
			else if (key.equals("description") && path.contains(".openAPIV3Schema")) {
				String original = null;
				original = (String) yaml.get(key);
				String code = "%" + path;
				List<String> codePair = new ArrayList<String>();
				codePair.add(code);
				codePair.add(original);
				keySheet.add(codePair);
				yaml.replace(key, code);
			}
		}
	}
}
