package com.gentics.mesh.alexa.intent.impl;

import static com.amazon.ask.request.Predicates.intentName;
import static com.gentics.mesh.alexa.util.I18NUtil.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.amazon.ask.attributes.AttributesManager;
import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.gentics.mesh.alexa.action.Attributes;
import com.gentics.mesh.alexa.action.MeshActions;
import com.gentics.mesh.alexa.intent.AbstractGenticsIntent;
import com.gentics.mesh.alexa.model.AlexaResponse;

@Singleton
public class ReserveTourIntent extends AbstractGenticsIntent {

	private final MeshActions mesh;

	@Inject
	public ReserveTourIntent(MeshActions mesh) {
		this.mesh = mesh;

	}

	@Override
	public boolean canHandle(HandlerInput input) {
		return input.matches(intentName("ReserveTour"));
	}

	@Override
	public Optional<Response> handle(HandlerInput input) {
		String speechText;
		Locale locale = getLocale(input);
		Slot tourSlot = getTourSlot(input);

		AttributesManager attributesManager = input.getAttributesManager();
		Map<String, Object> attributes = attributesManager.getSessionAttributes();
		String tourUuid = (String) attributes.get(Attributes.TOUR_UUID);
		String tourDate = (String) attributes.get(Attributes.TOUR_DATE);

		// Default
		speechText = i18n(locale, "tour_not_found");
		if (tourSlot != null && tourSlot.getValue() != null) {
			// TODO not yet implemented
			//String name = tourSlot.getValue();
			//AlexaResponse response = mesh.locateTourByName(locale, name).blockingGet();
			//speechText = response.getSpeech();
		} else if (tourUuid != null) {
			AlexaResponse response = mesh.reserveTourByUuid(locale, tourUuid, tourDate).blockingGet();
			speechText = response.getSpeech();
		}

		return input.getResponseBuilder()
			.withSpeech(speechText)
			.withSimpleCard(i18n(locale, "museum_name"), speechText)
			.withReprompt(i18n(locale, "fallback_answer"))
			.withShouldEndSession(false)
			.build();
	}

}
