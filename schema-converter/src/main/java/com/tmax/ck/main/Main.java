package com.tmax.ck.main;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

// 코드 전체에 warning을 무시하는 것은 위험하므로 주석처리
// @SuppressWarnings({ "unchecked" })
public class Main {
	// static List<List<String>> keySheet = new ArrayList<List<String>>();
	static Map<String, List<List<String>>> keySheetMap = new HashMap<String, List<List<String>>>();
	// static Integer sequence = new Integer(0);
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
			// schema 파일 혹은 CRD yaml 파일이 있는 root directory
			// String rootDir = "C:\\schema\\";
			String rootDir = "C:\\cicd-crd\\";
			String tempDir = Long.toString(System.currentTimeMillis());
			if (args.length % 2 != 0) {
				System.out.println("=================================================");
				System.out.println("ERROR: Wrong # of parameters");
				System.out.println("=================================================");
				return;
			}

			for (String arg : args) {
				switch (arg) {
					case "root":
						rootDir = args[Arrays.asList(args).indexOf(arg) + 1] + "/";
						break;
					case "output":
						tempDir = args[Arrays.asList(args).indexOf(arg) + 1];
						break;
					case "translate":
						autoTranslation = Boolean.parseBoolean(args[Arrays.asList(args).indexOf(arg) + 1]);
						break;
				}
			}
			String outputDir = rootDir + tempDir + "/";
			System.out.println(autoTranslation);

			Translate translate = null;
			if (autoTranslation) {
				translate = TranslateOptions.newBuilder().build().getService();
			}

			System.out.println("rootDir = " + rootDir);
			System.out.println("outputDir = " + outputDir);

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
			schemaMap = new HashMap<String, JsonObject>();
			for (File jsonFile : jsonFiles) {
				FileReader fr = new FileReader(jsonFile);
				schemaMap.put(jsonFile.getName(), gsonObj.fromJson(fr, JsonObject.class));
			}

			int iter = 0;
			for (File yamlFile : yamlFiles) {
				System.out.println("\n=================================================");
				System.out.printf("Start process for %s\n", yamlFile.getName());
				System.out.println("=================================================");
				// 새 파일을 처리하기 위해 Map객체들을 초기화
				yamlMap = new HashMap<String, Map<String, Object>>();
				keySheetMap = new HashMap<String, List<List<String>>>();

				// 하나의 yaml파일에 여러 CRD를 담고 있는 경우 여러개의 plural_name.yaml로 분리하고
				// 각 파일을 순회하며 yamlMap에 put
				ArrayList<String> plurals = splitYamlFileToMultipleFiles(yamlFile, outputDir);
				for (int i = 0; i < plurals.size(); i++) {
					// temp(count).yaml을 plural_name.yaml로 변경
					File oldFile = new File(outputDir + "temp" + Integer.toString(i) + ".yaml");
					File newFile = new File(outputDir + plurals.get(i).toString());
					oldFile.renameTo(newFile);

					// 형변환을 체크해주면 좋으나 일단 정상동작하므로 warning을 무시
					@SuppressWarnings({ "unchecked" })
					Map<String, Object> mappedObj = mapper.readValue(newFile, Map.class);
					yamlMap.put(plurals.get(i).toString(), mappedObj);
					newFile.delete();
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
					PrintWriter result = new PrintWriter(
							new BufferedWriter(
									new FileWriter(
											new File(outputDir + yamlFiles[iter].getName()), true)));
					mapper.writeValue(result, yamlMap.get(yamlKey));
					// mapper.writeValue(new File(outputDir + yamlKey), yamlMap.get(yamlKey));
				}

				// batch 처리를 위해서 작성한 코드. 현재 payload가 너무 크면 400 에러가 발생하는 문제가 있어서 주석처리
				// List<Translation> translatedList = translate.translate(originalList,
				// TranslateOption.sourceLanguage("en").targetLanguage("ko"));

				// CSV에서 콤마가 separator로 인식됨, 이스케이프 처리 하기 싫어서 OOXML 이용
				// 단일 파일에
				HSSFWorkbook workbook = new HSSFWorkbook();

				for (String sheetKey : keySheetMap.keySet()) {
					System.out.println("=================================================");
					System.out.printf("%s\n", sheetKey);
					System.out.println("=================================================");
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
							// String translated = translate
							// .translate(pair.get(1),
							// TranslateOption.sourceLanguage("en").targetLanguage("ko"))
							// .getTranslatedText();
							String translated = translate
									.translate(pair.get(1),
											TranslateOption.sourceLanguage("en"),
											TranslateOption.targetLanguage("ko"))
									.getTranslatedText();
							cell3.setCellValue(translated);
							System.out.println(translated);
						}

						// row 처리를 위한 인덱스 변수 증가
						i++;
					}
				}

				// output directory 에 output.xls 파일로 엑셀파일 저장
				String fileName = yamlFiles[iter].getName()
						.replace(".yaml", ".xls")
						.replace("crd-", "crd-translation-");
				workbook.write(new File(outputDir + fileName));
				workbook.close();

				// yaml file iteration count 증가
				iter++;
			}
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
		// System.out.println(yaml.keySet());
		for (String key : yaml.keySet()) {
			if (yaml.get(key) instanceof Map<?, ?>) {
				// 형변환을 체크해주면 좋으나 일단 정상동작하므로 warning을 무시
				@SuppressWarnings({ "unchecked" })
				Map<String, Object> convertedObj = (Map<String, Object>) yaml.get(key);
				convertCrdDescriptionToCode(keySheet, convertedObj, path + "." + key);
			}
			/*
			 * value가 List<?> 면서, 구성요소들이 Map<?, ?> 일 경우에 한정
			 * 구성요소들이 List<?> 일 경우 이슈 나오면 처리할 것임
			 */
			// value가 List 일 경우 예외처리(임시)
			else if (yaml.get(key) instanceof List<?>) {
				for (Object obj : convertObjToList(yaml.get(key))) {
					if (obj instanceof Map<?, ?>) {
						// 형변환을 체크해주면 좋으나 일단 정상동작하므로 warning을 무시
						@SuppressWarnings({ "unchecked" })
						Map<String, Object> convertedObj = (Map<String, Object>) obj;
						convertCrdDescriptionToCode(keySheet, convertedObj, path + "." + key);
					}
				}
			}
			/*
			 * jsonSchema 가 CRD yaml 아래에는 openAPIV3Schema 로 들어가는데, openAPIV3Schema 아닌 곳에도
			 * description을 key로 가지는 오브젝트 다수 발견
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

	// 하나의 yaml파일에 여러 CRD를 담고 있는 경우 각 CRD마다 crd_plural_name.yaml으로 분리하여 파일로 생성
	static ArrayList<String> splitYamlFileToMultipleFiles(File yamlFile, String outputDir)
			throws IOException {
		int cnt = 0;
		BufferedReader br = new BufferedReader(
				new FileReader(yamlFile));

		// file을 읽어야 plural name을 알 수 있기 때문에 file명을 temp(count).yaml로 일단 저장
		BufferedWriter bw = new BufferedWriter(
				new FileWriter(
						new File(outputDir + "temp" + Integer.toString(cnt) + ".yaml")));

		// plural name을 인식하기 위한 regular expression
		Pattern pattern = Pattern.compile("^\\s{4}plural: [a-zA-Z0-9]+$");
		ArrayList<String> plurals = new ArrayList<String>();
		String line = null;
		while ((line = br.readLine()) != null) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.matches()) {
				plurals.add(line.substring("    plural: ".length()) + ".yaml");
			}
			bw.write(line);
			bw.newLine();
			if (line.equals("---")) {
				cnt++;
				bw.flush();
				bw.close();
				bw = new BufferedWriter(
						new FileWriter(
								new File(outputDir + "temp" + Integer.toString(cnt) + ".yaml")));
			}
		}
		bw.flush();
		bw.close();
		br.close();

		// plural_name.yaml
		return plurals;
	}

	// object를 강제로 list로 캐스팅할경우 warning이 발생하므로 케이스를 나누어 object를 list로 변환
	static List<?> convertObjToList(Object obj) {
		if (obj.getClass().isArray()) {
			return Arrays.asList((Object[]) obj);
		} else if (obj instanceof Collection) {
			return new ArrayList<>((Collection<?>) obj);
		}

		return new ArrayList<>();
	}
}
