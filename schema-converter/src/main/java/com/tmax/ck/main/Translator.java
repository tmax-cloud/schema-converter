package com.tmax.ck.main;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.translate.v3.DetectLanguageRequest;
import com.google.cloud.translate.v3.DetectLanguageResponse;
import com.google.cloud.translate.v3.LocationName;
import com.google.cloud.translate.v3.TranslateTextRequest;
import com.google.cloud.translate.v3.TranslateTextResponse;
import com.google.cloud.translate.v3.TranslationServiceClient;

public class Translator {
	public static void main(String args[]) {
		String text = "HELLO";
		String from = detectLanguageOfText(text);
		String to = "ko";

		System.out.println("TRANSLATE STRING : " + text);
		System.out.println("TRANSLATE LANGUAGE FROM : " + from);
		System.out.println("TRANSLATE LANGUAGE TO : " + "ko");

		if (from.equals(to))
			System.out.println("TRANSLATE RESULT : " + text);
		else
			System.out.println("TRANSLATE RESULT : " + translateText(text, from, to));
	}

	static String translateText(String text, String sourceLanguageCode, String targetLanguageCode) {

		try (TranslationServiceClient translationServiceClient = TranslationServiceClient.create()) {

			LocationName locationName = LocationName.newBuilder().setProject("translate-project-307206")
					.setLocation("global").build();

			TranslateTextRequest translateTextRequest = TranslateTextRequest.newBuilder()

					.setParent(locationName.toString())

					.setMimeType("text/plain")

					.setSourceLanguageCode(sourceLanguageCode)

					.setTargetLanguageCode(targetLanguageCode)

					.addContents(text)

					.build();

			TranslateTextResponse response = translationServiceClient.translateText(translateTextRequest);

			return response.getTranslationsList().get(0).getTranslatedText();

		} catch (Exception e) {

			throw new RuntimeException("Couldn't create client.", e);

		}

	}

	static String detectLanguageOfText(String text) {
		try (TranslationServiceClient translationServiceClient = TranslationServiceClient.create()) {

			LocationName locationName = LocationName.newBuilder().setProject("translate-project-307206").setLocation("")
					.build();
			DetectLanguageRequest detectLanguageRequest = DetectLanguageRequest.newBuilder()
					.setParent(locationName.toString()).setMimeType("text/plain").setContent(text).build();

			DetectLanguageResponse response = translationServiceClient.detectLanguage(detectLanguageRequest);
			return response.getLanguages(0).getLanguageCode();

		} catch (Exception e) {
			throw new RuntimeException("Couldn't create client.", e);
		}
	}
}
