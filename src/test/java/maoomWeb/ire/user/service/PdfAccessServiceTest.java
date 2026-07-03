package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import maoomWeb.ire.user.mapper.PdfMapper;

class PdfAccessServiceTest {

    @Test
    void cachesRepeatedPdfAuthorizationChecks() {
        PdfMapper mapper = Mockito.mock(PdfMapper.class);
        when(mapper.hasPdfAccess(10L, "admin"))
                .thenReturn(true);
        PdfAccessService service =
                new PdfAccessService(mapper);

        service.requirePdf(10L, "admin");
        service.requirePdf(10L, "admin");
        service.requirePdf(10L, "admin");

        verify(mapper, times(1))
                .hasPdfAccess(10L, "admin");
    }

    @Test
    void deniesObjectWithoutDisclosingItsExistence() {
        PdfMapper mapper = Mockito.mock(PdfMapper.class);
        when(mapper.hasAttachmentAccess(20L, "other"))
                .thenReturn(false);
        PdfAccessService service =
                new PdfAccessService(mapper);

        assertThatThrownBy(() ->
                service.requireAttachment(20L, "other"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void allowsAnyAuthenticatedUserToReadRegisteredPdf() {
        PdfMapper mapper = Mockito.mock(PdfMapper.class);
        when(mapper.hasPdfAccess(10L, "reviewer"))
                .thenReturn(true);
        PdfAccessService service =
                new PdfAccessService(mapper);

        service.requirePdf(10L, "reviewer");

        verify(mapper).hasPdfAccess(10L, "reviewer");
    }

    @Test
    void limitsPdfManagementToOwnerOrAdministrator() {
        PdfMapper mapper = Mockito.mock(PdfMapper.class);
        when(mapper.isPdfOwner(10L, "owner"))
                .thenReturn(true);
        when(mapper.isPdfOwner(10L, "reviewer"))
                .thenReturn(false);
        when(mapper.hasPdfAccess(10L, "admin"))
                .thenReturn(true);
        PdfAccessService service =
                new PdfAccessService(mapper);

        service.requirePdfManagement(
                10L,
                "owner",
                false);
        service.requirePdfManagement(
                10L,
                "admin",
                true);

        assertThatThrownBy(() ->
                service.requirePdfManagement(
                        10L,
                        "reviewer",
                        false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }
}
