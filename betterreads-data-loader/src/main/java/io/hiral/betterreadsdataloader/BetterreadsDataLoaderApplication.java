package io.hiral.betterreadsdataloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import connection.DataStaxAstraProperties;
import io.hiral.betterreadsdataloader.author.Author;
import io.hiral.betterreadsdataloader.author.AuthorRepository;
import io.hiral.betterreadsdataloader.book.Book;
import io.hiral.betterreadsdataloader.book.BookRepository;

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
public class BetterreadsDataLoaderApplication {

	public static void main(String[] args) {
		SpringApplication.run(BetterreadsDataLoaderApplication.class, args);
	}

	private AuthorRepository authorRepository;

	@Autowired
	public void setRepo(AuthorRepository authorRepository) {
		this.authorRepository = authorRepository;
	}

	private BookRepository bookRepository;

	@Autowired
	public void setRepo(BookRepository bookRepository) {
		this.bookRepository = bookRepository;
	}

	@Value("${datadump.location.author}")
	private String authorDumpLocation;

	@Value("${datadump.location.works}")
	private String worksDumpLocation;

	@PostConstruct
	public void start() {
		// initAuthors();
		initWorks();
	}

	private void initAuthors() {
		Path path = Paths.get(authorDumpLocation);
		try (Stream<String> lines = Files.lines(path)) {
			lines.forEach(line -> {
				// read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				JSONObject jsonObject;
				try {
					jsonObject = new JSONObject(jsonString);

					// construct author object
					Author author = new Author();
					author.setName(jsonObject.optString("name"));
					author.setPersonalName(jsonObject.optString("personal_key"));
					author.setId(jsonObject.optString("key").replace("/authors/", ""));

					// persist using repository
					System.out.println("Saving author " + author.getName() + "....");
					authorRepository.save(author);

				} catch (JSONException e) {
					e.printStackTrace();
				}

			});
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void initWorks() {
		Path path = Paths.get(worksDumpLocation);
		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
		try (Stream<String> lines = Files.lines(path)) {
			lines.limit(50).forEach(line -> {
				// read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				JSONObject jsonObject;
				try {
					jsonObject = new JSONObject(jsonString);

					// construct book object
					Book book = new Book();

					book.setId(jsonObject.optString("key").replace("/works/", ""));

					book.setName(jsonObject.optString("title"));
					JSONObject descriptionObj = jsonObject.optJSONObject("description");
					if (descriptionObj != null) {
						book.setDescription(descriptionObj.optString("value"));
					}
					JSONObject publishedObj = jsonObject.optJSONObject("created");
					if (publishedObj != null) {
						String dateStr = publishedObj.getString("value");
						book.setPublishedDate(LocalDate.parse(dateStr, dateFormat));
					}

					JSONArray coversJSONArray = jsonObject.optJSONArray("covers");
					if (coversJSONArray != null) {
						List<String> coverIds = new ArrayList<>();
						for (int i = 0; i < coversJSONArray.length(); i++) {
							coverIds.add(coversJSONArray.getString(i));
						}
						book.setCoverIds(coverIds);
					}

					JSONArray authorsJSONArray = jsonObject.optJSONArray("authors");
					if (authorsJSONArray != null) {
						List<String> authorIds = new ArrayList<>();
						for (int i = 0; i < authorsJSONArray.length(); i++) {
							String authorId = authorsJSONArray.getJSONObject(i).getJSONObject("author").getString("key")
									.replace("/authors/", "");
							authorIds.add(authorId);
						}
						book.setAuthorIds(authorIds);

						List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id))
								.map(optionalAuthor -> {
									if (!optionalAuthor.isPresent())
										return "Unknown Author";
									return optionalAuthor.get().getName();
								}).collect(Collectors.toList());
						book.setAuthorNames(authorNames);

						// persist using repository
						System.out.println("Saving book  " + book.getName() + "....");
						bookRepository.save(book);
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}

			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
		Path bundle = astraProperties.getSecureConnectBundle().toPath();
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}

}
