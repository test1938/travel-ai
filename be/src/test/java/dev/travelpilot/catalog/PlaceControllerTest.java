package dev.travelpilot.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaceController.class)
@Import({PlaceService.class, GlobalExceptionHandler.class})
class PlaceControllerTest {

    static final Place BA_NA = new CatalogReader().readAll().stream()
            .filter(p -> p.id().equals("danang-way-359114064")).findFirst().orElseThrow();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlaceRepository repository;

    @Test
    void filtersByDestinationAndHyphenatedTag() throws Exception {
        PlaceQuery expected = new PlaceQuery(Destination.DANANG, null, PlaceTag.THEME_PARK, 0, 20);
        when(repository.find(expected)).thenReturn(List.of(BA_NA));
        when(repository.count(expected)).thenReturn(1L);

        mvc.perform(get("/api/v1/places").param("destination", "DANANG").param("tag", "THEME-PARK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("danang-way-359114064"))
                .andExpect(jsonPath("$.items[0].name").value("Sun World Bà Nà Hills"))
                .andExpect(jsonPath("$.items[0].tags[?(@ == 'THEME-PARK')]").exists())
                .andExpect(jsonPath("$.items[0].estCostVnd").value(1_000_000));

        verify(repository).find(expected);
    }

    @Test
    void unknownTagIs400() throws Exception {
        mvc.perform(get("/api/v1/places").param("tag", "ZOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownDestinationIs400() throws Exception {
        mvc.perform(get("/api/v1/places").param("destination", "PHUQUOC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void pageSizeIsLimited() throws Exception {
        mvc.perform(get("/api/v1/places").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void getById() throws Exception {
        when(repository.findById("danang-way-359114064")).thenReturn(Optional.of(BA_NA));
        mvc.perform(get("/api/v1/places/danang-way-359114064"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayTrip").value(true));
    }

    @Test
    void unknownIdIs404() throws Exception {
        when(repository.findById(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/places/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
