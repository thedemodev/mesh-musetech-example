package com.gentics.mesh.alexa.action;

import static com.gentics.mesh.alexa.util.I18NUtil.i18n;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;

import com.gentics.mesh.alexa.dagger.config.SkillConfig;
import com.gentics.mesh.alexa.intent.impl.TourInfoIntentHandler;
import com.gentics.mesh.alexa.model.TourInfo;
import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.NodeUpdateRequest;
import com.gentics.mesh.core.rest.node.field.MicronodeField;
import com.gentics.mesh.core.rest.node.field.impl.DateFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.NumberFieldImpl;
import com.gentics.mesh.core.rest.node.field.list.MicronodeFieldList;
import com.gentics.mesh.parameter.client.NodeParametersImpl;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshRestClientConfig;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

@Singleton
public class MeshActions {

	private static final Logger log = LoggerFactory.getLogger(TourInfoIntentHandler.class);

	private final String PROJECT = "musetech";

	private final MeshRestClient client;

	private final JsonObject searchTourQuery;

	private final String loadToursInfoQuery;

	@Inject
	public MeshActions(SkillConfig config) {

		MeshRestClientConfig clientConfig = MeshRestClientConfig.newConfig()
			.setHost(config.getMeshServerHost())
			.setPort(config.getMeshServerPort())
			.setSsl(config.isMeshServerSslFlag())
			.setBasePath("/api/v2")
			.build();

		client = MeshRestClient.create(clientConfig);
		String apiKey = config.getMeshApiKey();
		if (apiKey != null) {
			client.setAPIKey(apiKey);
		} else {
			client.setLogin("admin", "admin");
			client.login().blockingGet();
		}
		try {
			searchTourQuery = loadJson("/queries/searchTour.json");
			loadToursInfoQuery = loadString("/queries/toursInfo.gql");
		} catch (Exception e) {
			throw new RuntimeException("Could not find query.");
		}
	}

	private String loadString(String path) throws IOException {
		return IOUtils.toString(this.getClass().getResourceAsStream(path), "UTF-8");
	}

	private JsonObject loadJson(String path) throws IOException {
		return new JsonObject(loadString(path));
	}

	public Single<String> loadTourInfos(Locale locale) {

		return client.graphqlQuery(PROJECT, loadToursInfoQuery).toMaybe().map(response -> {
			System.out.println(response.toJson());
			JsonObject json = response.getData();
			JsonArray tours = json.getJsonObject("schema").getJsonObject("nodes").getJsonArray("elements");

			if (tours.size() == 0) {
				return i18n(locale, "tours_empty");
			}

			StringBuilder builder = new StringBuilder();
			builder.append(i18n(locale, "tour_info_intro"));
			builder.append(" ");
			for (int i = 0; i < tours.size(); i++) {
				JsonObject tour = tours.getJsonObject(i);
				JsonObject tourFields = tour.getJsonObject("fields");
				String title = tourFields.getString("title");
				int size = tourFields.getInteger("size");
				double price = tourFields.getDouble("price");
				JsonArray dates = tourFields.getJsonArray("dates");
				builder.append(i18n(locale, "tour_info", title, String.valueOf(size)));
				if (tours.size() >= 2 && i == tours.size() - 2) {
					builder.append(" " + i18n(locale, "and") + " ");
				} else {
					builder.append(". ");
				}
			}
			return builder.toString();
		})
			.onErrorReturnItem(i18n(locale, "tours_empty"))
			.toSingle();
	}

	public Maybe<TourInfo> loadNextTour(Locale locale) {
		JsonObject vars = new JsonObject();
		vars.put("lang", locale.getLanguage());
		GraphQLRequest request = new GraphQLRequest();
		request.setQuery(loadToursInfoQuery);
		request.setVariables(vars);
		return client.graphql(PROJECT, request).toMaybe().map(response -> {
			// System.out.println(response.toJson());
			JsonObject json = response.getData();
			JsonArray tours = json.getJsonObject("schema").getJsonObject("nodes").getJsonArray("elements");
			return findNextTour(tours);
		});
	}

	public Single<String> loadNextTourInfo(Locale locale) {

		return loadNextTour(locale).map(tour -> {
			if (tour == null) {
				return i18n(locale, "tours_empty");
			} else {
				// OffsetDateTime.now().
				OffsetDateTime date = tour.getDate();
				boolean today = date.toLocalDate().isEqual(OffsetDateTime.now().toLocalDate());
				StringBuilder builder = new StringBuilder();
				String timeStr = date.getHour() + ":" + date.getMinute();
				String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
				if (today) {
					builder.append(i18n(locale, "tour_next_info_today", tour.getTitle(), timeStr, tour.getLocation()));
					builder.append(" ");
					if (tour.getSeats() == 1) {
						builder.append(i18n(locale, "tour_next_info_seat"));
					} else {
						builder.append(i18n(locale, "tour_next_info_seats", String.valueOf(tour.getSeats())));
					}
				} else {
					// builder.append(i18n(locale, "tour_next_info_tomorrow"));
					builder.append(i18n(locale, "tour_next_info_on", tour.getTitle(), dateStr, timeStr, tour.getLocation()));
				}
				builder.append(" ");
				return builder.toString();

			}

		})
			.onErrorReturnItem(i18n(locale, "tours_empty"))
			.toSingle();
	}

	private TourInfo findNextTour(JsonArray tours) {

		TourInfo earliestInfo = null;
		for (int i = 0; i < tours.size(); i++) {
			JsonObject tour = tours.getJsonObject(i);
			JsonObject tourFields = tour.getJsonObject("fields");
			String uuid = tour.getString("uuid");
			String title = tourFields.getString("title");
			String location = tourFields.getString("location");
			int size = tourFields.getInteger("size");
			double price = tourFields.getDouble("price");
			JsonArray dates = tourFields.getJsonArray("dates");
			if (dates.isEmpty()) {
				break;
			}
			for (int e = 0; e < dates.size(); e++) {
				JsonObject tourDate = dates.getJsonObject(e);
				JsonObject tourDateFields = tourDate.getJsonObject("fields");
				int seats = tourDateFields.getInteger("seats");
				String dateStr = tourDateFields.getString("date");
				OffsetDateTime date = null;
				try {
					date = OffsetDateTime.parse(dateStr);
				} catch (Exception e2) {
					log.error("Could not parse date {" + dateStr + "}");
				}
				if (date != null && (earliestInfo == null || date.isBefore(earliestInfo.getDate())) && seats != 0) {
					earliestInfo = new TourInfo(uuid, title, location, date, price, seats, size);
				}
			}
		}
		return earliestInfo;

	}

	public Single<String> loadStockLevel(Locale locale, String tourName) {
		return locateTour(tourName).map(node -> {
			Long level = getStockLevel(node);
			if (level == null || level == 0) {
				return i18n(locale, "tour_out_of_stock", getName(node));
			} else if (level == 1) {
				return i18n(locale, "tour_stock_level_one", getName(node));
			} else {
				return i18n(locale, "tour_stock_level", String.valueOf(level));
			}
		})
			.onErrorReturnItem(i18n(locale, "tour_stock_level_error"))
			.defaultIfEmpty(i18n(locale, "tour_not_found"))
			.toSingle();
	}

	public Single<String> reserveNextTour(Locale locale) {
		// 1. Locate the next tour
		return loadNextTour(locale).flatMapSingle(tour -> {

			if (tour.getSeats() == 0) {
				return Single.just(i18n(locale, "tour_out_of_stock", tour.getTitle()));
			}

			// 2. Load and update the tour node in both languages
			return Observable.fromArray("de", "en").flatMapCompletable(lang -> {
				Maybe<NodeResponse> locatedNode = client
					.findNodeByUuid(PROJECT, tour.getUuid(), new NodeParametersImpl().setLanguages(lang)).toMaybe();

				return locatedNode.flatMapCompletable(node -> {
					NodeUpdateRequest nodeUpdateRequest = node.toRequest();
					updateRequest(nodeUpdateRequest, tour);
					System.out.println(nodeUpdateRequest.toJson());
					Completable update = client.updateNode(PROJECT, node.getUuid(), nodeUpdateRequest).toCompletable();
					return update;
				});
			}).andThen(Single.just(i18n(locale, "tour_reserved", tour.getTitle())));
		}).onErrorReturnItem(i18n(locale, "tour_reserve_error"));
	}

	private void updateRequest(NodeUpdateRequest nodeUpdateRequest, TourInfo tour) {
		MicronodeFieldList list = nodeUpdateRequest.getFields().getMicronodeFieldList("dates");

		for (MicronodeField date : list.getItems()) {
			DateFieldImpl currentDateField = date.getFields().getDateField("date");
			if (currentDateField != null) {
				OffsetDateTime odt = OffsetDateTime.parse(currentDateField.getDate());

				// Found the next tour. Lets update the count
				if (tour.getDate().isEqual(odt)) {
					int currentSeats = date.getFields().getNumberField("seats").getNumber().intValue();
					int newSeats = currentSeats - 1;
					date.getFields().put("seats", new NumberFieldImpl().setNumber(newSeats));
				}
			}
		}

		nodeUpdateRequest.getFields().put("dates", list);

	}

	public Single<String> loadTourPrice(Locale locale, String tourName) {
		return locateTour(tourName).map(node -> {
			NumberFieldImpl price = node.getFields().getNumberField("price");
			double value = price.getNumber().doubleValue();
			String priceStr = String.format("%.2f Euro", value);
			String name = getName(node);
			log.info("Located tour for " + tourName + " => " + name);
			return i18n(locale, "tour_price", name, priceStr);
		})
			.defaultIfEmpty(i18n(locale, "tour_not_found"))
			.onErrorReturnItem(i18n(locale, "tour_price_not_found", tourName))
			.toSingle();
	}

	private Maybe<NodeResponse> locateTour(String tourName) {
		if (tourName == null) {
			return Maybe.empty();
		}
		JsonObject query = new JsonObject(searchTourQuery.encode());
		query.getJsonObject("query").getJsonObject("bool").getJsonArray("must").getJsonObject(1).getJsonObject("match").put("fields.title",
			tourName.toLowerCase());
		log.info("Sending search request:\n\n" + query.encodePrettily());
		return client.searchNodes(PROJECT, query.encode()).toMaybe()
			.onErrorComplete()
			.defaultIfEmpty(new NodeListResponse())
			.flatMap(list -> {
				if (list.getData().isEmpty()) {
					log.info("No result found");
					return Maybe.empty();
				} else {
					log.info("Found {" + list.getData().size() + "} matches. Using the first.");
					return Maybe.just(list.getData().get(0));
				}
			});
	}

	private Long getStockLevel(NodeResponse response) {
		MicronodeFieldList dates = response.getFields().getMicronodeFieldList("dates");
		MicronodeField first = dates.getItems().get(0);
		Number number = first.getFields().getNumberField("seats").getNumber();
		if (number == null) {
			return null;
		}
		return number.longValue();
	}

	private String getName(NodeResponse node) {
		return node.getFields().getStringField("title").getString();
	}

}
