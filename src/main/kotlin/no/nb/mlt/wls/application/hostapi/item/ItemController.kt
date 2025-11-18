package no.nb.mlt.wls.application.hostapi.item

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.callbacks.Callback
import io.swagger.v3.oas.annotations.callbacks.Callbacks
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.enums.ParameterStyle
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nb.mlt.wls.application.hostapi.ErrorMessage
import no.nb.mlt.wls.application.hostapi.config.checkIfAuthorized
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.AddNewItem
import no.nb.mlt.wls.domain.ports.inbound.EditItem
import no.nb.mlt.wls.domain.ports.inbound.GetItem
import no.nb.mlt.wls.infrastructure.callbacks.NotificationItemPayload
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping(path = ["/hermes/v1"])
@Tag(name = "Item Controller", description = """API for creating items in Hermes WLS""")
class ItemController(
    private val getItem: GetItem,
    private val addNewItem: AddNewItem,
    private val editItem: EditItem
) {
    @Operation(
        summary = "Register an item in Hermes",
        description = """Register data about the item in Hermes WLS and appropriate storage systems.
            This step is required to store the item in the physical storage systems, as they need to have metadata about the object.
            An item is also called product by some storage systems and users, those mean the same thing to Hermes."""
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = """Item with given "hostName" and "hostId" already exists in the system.
                    No new item was created, neither was the old item updated.
                    Existing item information is returned for inspection.""",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiItemPayload::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "201",
                description = """Item payload is valid and the item information was registered successfully in Hermes WLS.
                    Item metadata will be created in the appropriate storage system(s).
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
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorMessage::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "401",
                description = """Client sending the request is not authorized to operate on items.""",
                content = [
                    Content(
                        mediaType = "string",
                        schema = Schema(implementation = String::class, example = "Unauthorized")
                    )
                ]
            ),
            ApiResponse(
                responseCode = "403",
                description = """A valid "Authorization" header is missing from the request.""",
                content = [
                    Content(
                        mediaType = "string",
                        schema = Schema(implementation = String::class, example = "Forbidden")
                    )
                ]
            )
        ]
    )
    @Callbacks(
        Callback(
            name = "Item Callback",
            callbackUrlExpression = $$"{$request.body#/callbackUrl}",
            operation =
                arrayOf(
                    Operation(
                        summary = "Notification about updated item",
                        description = """This callback triggers when item information is updated inside the storage systems.
                        It contains the full data of the item, including the current quantity and location of it.
                        Situations where this callback is triggered may include: item moves in storage,
                        item is picked for order, item is returned to storage, etc.""",
                        method = "post",
                        parameters =
                            arrayOf(
                                Parameter(
                                    name = "X-Signature",
                                    description = "HMAC SHA-256 signature of timestamp and the payload",
                                    `in` = ParameterIn.HEADER,
                                    style = ParameterStyle.SIMPLE,
                                    example = "iBUWzWuoRH05IWVjxUcNwRa260OfXR8Cpo90tcQL5rw=",
                                    required = true,
                                    schema = Schema(type = "string")
                                ),
                                Parameter(
                                    name = "X-Timestamp",
                                    description = "Timestamp for when the message was sent",
                                    `in` = ParameterIn.HEADER,
                                    style = ParameterStyle.SIMPLE,
                                    example = "1747467000",
                                    required = true,
                                    schema = Schema(type = "string")
                                )
                            ),
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
        @RequestBody @Valid payload: ApiCreateItemPayload
    ): ResponseEntity<ApiItemPayload> {
        jwt.checkIfAuthorized(payload.hostName)

        getItem.getItem(payload.hostName, payload.hostId)?.let {
            return ResponseEntity
                .status(HttpStatus.OK)
                .body(it.toApiPayload())
        }

        val itemCreated = addNewItem.addItem(payload.toItemMetadata())

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(itemCreated.toApiPayload())
    }

    @Operation(
        summary = "Retrieve info about an item from Hermes",
        description = """Retrieve data about the item that Hermes WLS has registered.
            We try our best to have the most up-to-date information about an item.
            This is not always possible as we are limited by what storage systems provide us.
            Especially in case of items stored in manual storages we might not always have correct info on hand."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Information about the item with given "hostname" and "hostId"""",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiItemPayload::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Some fields in your request are invalid.
                The error message contains information about the invalid fields.""",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorMessage::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to request item info, or this item does not belong to them.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        ),
        ApiResponse(
            responseCode = "404",
            description = """The item with given "hostname" and "hostId" does not exist in the system.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Not Found")
                )
            ]
        )
    )
    @GetMapping("/item/{hostName}/{hostId}")
    suspend fun getItem(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @Parameter(
            description = """Name of the host system which owns the item.""",
            required = true,
            allowEmptyValue = false,
            example = "AXIELL"
        )
        @PathVariable("hostName") hostName: HostName,
        @Parameter(
            description = """ID of the item which you wish to retrieve.""",
            required = true,
            allowEmptyValue = false,
            example = "mlt-12345"
        )
        @PathVariable("hostId") hostId: String
    ): ResponseEntity<ApiItemPayload> {
        jwt.checkIfAuthorized(hostName)

        val item = getItem.getItem(hostName, hostId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(item.toApiPayload())
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Updated information about the item with given "hostname" and "hostId"""",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiItemPayload::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Some fields in your request are invalid.
                The error message contains information about the invalid fields.""",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorMessage::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to edit item info, or this item does not belong to them.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        ),
        ApiResponse(
            responseCode = "404",
            description = """The item with given "hostname" and "hostId" does not exist in the system.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Not Found")
                )
            ]
        )
    )
    suspend fun updateItem(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @Parameter(
            description = """Name of the host system which owns the item.""",
            required = true,
            allowEmptyValue = false,
            example = "AXIELL"
        )
        @PathVariable("hostName") hostName: HostName,
        @Parameter(
            description = """ID of the item which you wish to update.""",
            required = true,
            allowEmptyValue = false,
            example = "mlt-12345"
        )
        @PathVariable("hostId") hostId: String,
        @RequestBody @Valid payload: ApiEditItemPayload
    ): ResponseEntity<ApiItemPayload> {
        jwt.checkIfAuthorized(hostName)

        val item = getItem.getItem(hostName, hostId) ?: return ResponseEntity.notFound().build()

        val editedItem = editItem.editItem(item, payload.toItemEditMetadata())

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(editedItem.toApiPayload())
    }
}
