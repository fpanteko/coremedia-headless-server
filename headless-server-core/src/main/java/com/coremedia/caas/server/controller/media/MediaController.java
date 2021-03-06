package com.coremedia.caas.server.controller.media;

import com.coremedia.caas.server.CaasServiceConfig;
import com.coremedia.caas.server.controller.base.ControllerBase;
import com.coremedia.caas.server.controller.base.ResponseStatusException;
import com.coremedia.caas.server.service.media.ImageVariantsDescriptor;
import com.coremedia.caas.server.service.media.ImageVariantsResolver;
import com.coremedia.caas.server.service.media.MediaResource;
import com.coremedia.caas.server.service.media.MediaResourceModel;
import com.coremedia.caas.server.service.media.MediaResourceModelFactory;
import com.coremedia.caas.service.repository.RootContext;
import com.coremedia.caas.service.security.AccessControlViolation;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/caas/v1/{tenantId}/sites/{siteId}/media")
@Api(value = "/caas/v1/{tenantId}/sites/{siteId}/media", tags = "Media", description = "Operations for media objects")
public class MediaController extends ControllerBase {

  @SuppressWarnings("WeakerAccess")
  public static final String HANDLER_NAME_MEDIA_DATA = "mediaData";
  @SuppressWarnings("WeakerAccess")
  public static final String HANDLER_NAME_MEDIA_DATA_WITH_HASH = "mediaDataWithHash";
  @SuppressWarnings("WeakerAccess")
  public static final String HANDLER_NAME_IMAGE_VARIANTS = "imageVariants";


  @Autowired
  private CaasServiceConfig serviceConfig;

  @Autowired
  private ImageVariantsResolver imageVariantsResolver;


  public MediaController() {
    super("caas.server.media.requests");
  }


  private ResponseEntity getMedia(String tenantId, String siteId, MediaResourceModel resourceModel, String ratio, Integer minWidth, Integer minHeight) {
    String contentType = resourceModel.getType();
    String requestedRatio = ratio != null ? ratio : "none";
    return execute(() -> {
      MediaResource resource = resourceModel.getMediaResource(ratio, minWidth, minHeight);
      if (resource != null) {
        // send response with appropriate cache headers
        CacheControl cacheControl;
        if (serviceConfig.isPreview()) {
          cacheControl = CacheControl.noCache();
        }
        else {
          long maxAge = getMaxAge(serviceConfig.getMediaCacheTime(resource.getMediaType()));
          cacheControl = CacheControl.maxAge(maxAge, TimeUnit.SECONDS).mustRevalidate();
        }
        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .contentType(resource.getMediaType())
                .eTag(resource.getETag())
                .body(resource);
      }
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }, "tenant", tenantId, "site", siteId, "type", contentType, "ratio", requestedRatio);
  }


  @ResponseBody
  @RequestMapping(name = HANDLER_NAME_MEDIA_DATA, value = "/{mediaId}/{propertyName}", method = RequestMethod.GET)
  @ApiOperation(
          value = "Media.Blob",
          notes = "Return the binary data of a media object.\n" +
                  "Images can be cropped and scaled according to the responsive image settings of the site.",
          response = Byte.class,
          responseContainer = "Array"
  )
  @ApiResponses(value = {
          @ApiResponse(code = 400, message = "Invalid tenant or site"),
          @ApiResponse(code = 404, message = "Media object not found")
  })
  public ResponseEntity getMedia(@ApiParam(value = "The tenant's unique ID", required = true) @PathVariable String tenantId,
                                 @ApiParam(value = "The site's unique ID", required = true) @PathVariable String siteId,
                                 @ApiParam(value = "The media object's numeric ID or alias", required = true) @PathVariable String mediaId,
                                 @ApiParam(value = "The blob property name", required = true) @PathVariable String propertyName,
                                 @ApiParam(value = "The required ratio if requesting an image") @RequestParam(required = false) String ratio,
                                 @ApiParam(value = "The required minimum width if requesting an image") @RequestParam(required = false, defaultValue = "-1") Integer minWidth,
                                 @ApiParam(value = "The required minimum height if requesting an image") @RequestParam(required = false, defaultValue = "-1") Integer minHeight,
                                 ServletWebRequest request) {
    try {
      RootContext rootContext = resolveRootContext(tenantId, siteId, mediaId, request);
      // create model for media data
      MediaResourceModel resourceModel = rootContext.getModelFactory().createModel(MediaResourceModelFactory.MODEL_NAME, rootContext.getTarget(), propertyName);
      if (resourceModel != null) {
        return getMedia(tenantId, siteId, resourceModel, ratio, minWidth, minHeight);
      }
      return null;
    } catch (AccessControlViolation e) {
      return handleError(e, request);
    } catch (ResponseStatusException e) {
      return handleError(e, request);
    } catch (Exception e) {
      return handleError(e, request);
    }
  }

  @ResponseBody
  @RequestMapping(name = HANDLER_NAME_MEDIA_DATA_WITH_HASH, value = "/{mediaId}/{propertyName}/{mediaHash}", method = RequestMethod.GET)
  @ApiOperation(
          value = "Media.BlobWithHash",
          notes = "Return the binary data of a media object.\n" +
                  "Images can be cropped and scaled according to the responsive image settings of the site.\n" +
                  "The hash included in the URL path is validated against the media object to ensure it has not been\n" +
                  "modified, thus allowing 'unlimited' cache times\n",
          response = Byte.class,
          responseContainer = "Array"
  )
  @ApiResponses(value = {
          @ApiResponse(code = 302, message = "Hash is invalid"),
          @ApiResponse(code = 400, message = "Invalid tenant or site"),
          @ApiResponse(code = 404, message = "Media object not found")
  })
  public ResponseEntity getMedia(@ApiParam(value = "The tenant's unique ID", required = true) @PathVariable String tenantId,
                                 @ApiParam(value = "The site's unique ID", required = true) @PathVariable String siteId,
                                 @ApiParam(value = "The media object's numeric ID or alias", required = true) @PathVariable String mediaId,
                                 @ApiParam(value = "The blob property name", required = true) @PathVariable String propertyName,
                                 @ApiParam(value = "The media object's hash", required = true) @PathVariable String mediaHash,
                                 @ApiParam(value = "The required ratio if requesting an image") @RequestParam(required = false) String ratio,
                                 @ApiParam(value = "The required minimum width if requesting an image") @RequestParam(required = false, defaultValue = "-1") Integer minWidth,
                                 @ApiParam(value = "The required minimum height if requesting an image") @RequestParam(required = false, defaultValue = "-1") Integer minHeight,
                                 ServletWebRequest request) {
    try {
      RootContext rootContext = resolveRootContext(tenantId, siteId, mediaId, request);
      // create model for media data
      MediaResourceModel resourceModel = rootContext.getModelFactory().createModel(MediaResourceModelFactory.MODEL_NAME, rootContext.getTarget(), propertyName);
      if (resourceModel != null) {
        // validate hash
        String modelHash = resourceModel.getHash();
        if (!modelHash.equals(mediaHash)) {
          UriComponentsBuilder builder = MvcUriComponentsBuilder.fromMethodCall(
                  on(MediaController.class).getMedia(
                          tenantId,
                          siteId,
                          mediaId,
                          propertyName,
                          modelHash,
                          ratio,
                          minWidth == -1 ? null : minWidth,
                          minHeight == -1 ? null : minHeight,
                          null));
          URI redirectUri = builder.build().encode().toUri();
          // temporarily redirect to matching URI if not matching
          return ResponseEntity.status(HttpStatus.FOUND)
                  .cacheControl(CacheControl.maxAge(10, TimeUnit.SECONDS))
                  .location(redirectUri)
                  .build();
        }
        return getMedia(tenantId, siteId, resourceModel, ratio, minWidth, minHeight);
      }
      return null;
    } catch (AccessControlViolation e) {
      return handleError(e, request);
    } catch (ResponseStatusException e) {
      return handleError(e, request);
    } catch (Exception e) {
      return handleError(e, request);
    }
  }


  @ResponseBody
  @RequestMapping(name = HANDLER_NAME_IMAGE_VARIANTS, value = "/image/variants", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(
          value = "Media.ImageVariants",
          notes = "Return the delivery variants of an image.\n" +
                  "The variants consist of specified aspect ratios in different resolutions.",
          response = ImageVariantsDescriptor.class
  )
  @ApiResponses(value = {
          @ApiResponse(code = 200, message = "The transformation map", response = ImageVariantsDescriptor.class),
          @ApiResponse(code = 400, message = "Invalid tenant or site")
  })
  public ResponseEntity<ImageVariantsDescriptor> getMediaVariants(@ApiParam(value = "The tenant's unique ID", required = true) @PathVariable String tenantId,
                                                                  @ApiParam(value = "The site's unique ID", required = true) @PathVariable String siteId,
                                                                  ServletWebRequest request) {
    try {
      RootContext rootContext = resolveRootContext(tenantId, siteId, request);
      return ResponseEntity.ok()
              .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS))
              .body(imageVariantsResolver.getVariantsDescriptor(rootContext.getSite()));
    } catch (AccessControlViolation e) {
      return handleError(e, request);
    } catch (ResponseStatusException e) {
      return handleError(e, request);
    } catch (Exception e) {
      return handleError(e, request);
    }
  }
}
