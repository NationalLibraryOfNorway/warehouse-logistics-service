package no.nb.mlt.wls.application.hostapi.item

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.callbacks.Callback
import io.swagger.v3.oas.annotations.callbacks.Callbacks
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.application.hostapi.config.checkIfAuthorized
import no.nb.mlt.wls.domain.ports.inbound.AddNewItem
import no.nb.mlt.wls.domain.ports.inbound.GetItem
import no.nb.mlt.wls.infrastructure.callbacks.NotificationItemPayload
import org.springframework.data.mongodb.core.validation.Validator.schema
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping(path = [ "/v1"])
@Tag(name = "Item Controller", description = "API endpoints used by catalogs for managing items in Hermes WLS")
class ItemController(
    private val addNewItem: AddNewItem,
    private val getItem: GetItem
) {
    @Operation(
        summary = "Register an item in Hermes",
        description = """Register data about the item in Hermes WLS and appropriate storage systems.
            This is required step to store the item in physical storage system.
            An item is also called product by some storage systems and users, those mean the same thing to Hermes.
            NOTE: When registering new item quantity and location are set to default values (0 and null).
            Hence you should be aware that these values will be overwritten."""
    )

    // ? TODO: Do we need to handle item location differently? Maybe set it to "UNKNOWN" instead of null?
    // ? More explicit and shows we are aware of it.
    // ? Also I think item model errors if location is null or empty string?

    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = """Item with given "hostName" and "hostId" already exists in the system.
                    No new item was created, neither was the old item updated.
                    Existing item information is returned for inspection.
                    In some rare cases the response body may be empty.
                    That can happen if Hermes WLS does not have the information about the item stored in its database,
                    and is unable to retrieve the existing item information from the storage system.""",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiItemPayload::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "201",
                description = """Item payload is valid and the item information was registered successfully.
                    Item was created in the appropriate storage systems.
                    New item information is returned for inspection.""",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiItemPayload::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "400",
                description = """Item payload is invalid, no new item was created.
                    Error message contains information about the invalid field(s).""",
                content = [Content(schema = Schema())]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Client sending the request is not authorized to operate on items.",
                content = [Content(schema = Schema())]
            ),
            ApiResponse(
                responseCode = "403",
                description = "A valid 'Authorization' header is missing from the request.",
                content = [Content(schema = Schema())]
            )
        ]
    )
    @Callbacks(
        Callback(
            name = "Item Callback",
            callbackUrlExpression = "{\$request.body#/callbackUrl}",
            operation =
                arrayOf(
                    Operation(
                        summary = "Notification of updated item",
                        description = """This callback triggers when an item is updated inside the storage systems.
                        It contains the full data of the item, including the current quantity and location of it.
                        Situations where this callback is triggered may include: item moves in storage,
                        item is picked for order, item is returned to storage, etc.""",
                        method = "post",
                        requestBody =
                            SwaggerRequestBody(
                                content = [Content(schema = Schema(implementation = NotificationItemPayload::class))],
                                required = true
                            )
                    )
                )
        )
    )
    @PostMapping("/item")
    suspend fun createItem(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @RequestBody payload: ApiItemPayload
    ): ResponseEntity<ApiItemPayload> {
        jwt.checkIfAuthorized(payload.hostName)
        payload.validate()

        getItem.getItem(payload.hostName, payload.hostId)?.let {
            return ResponseEntity.ok(it.toApiPayload())
        }

        val item = addNewItem.addItem(payload.toItemMetadata())

        return ResponseEntity(item.toApiPayload(), HttpStatus.CREATED)
    }
}
