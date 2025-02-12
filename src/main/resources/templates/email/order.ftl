<head>
    <title>WLS Bestillingsbekreftelse</title>
    <style>
        .wrapper-class {
            display: flex;
            flex-direction: column;
        }

        .requires-spacing {
            margin-bottom: 15px;
            gap: 5px;
        }

        .table-class {
            width: 100%;
        }

        td {
            border: 2px solid;
        }
    </style>
</head>

<div class="wrapper-class">
    <div class="requires-spacing">
        <div>
            En ny bestilling har blitt mottatt fra ${order.hostName}.
        </div>
        <div>
            ${orderQrCode}
        </div>
        <div>
            <b>Lån:</b> ${order.hostOrderId}
        </div>
        <div>
            <b>Bestillingstype:</b> ${orderType}
        </div>
        <div>
            <b>Kontaktperson:</b> ${order.contactPerson}
        </div>
        <div>
            <b>Melding fra bestiller:</b> ${order.note}
        </div>
    </div>
    <div>
        <b>Ordrelinjer</b>
        <table class="table-class">
            <tr>
                <td>
                    <b>Beskrivelse</b>
                </td>
                <td>
                    <b>Objekt ID</b>
                </td>
                <td>
                    <b>Lokasjon</b>
                </td>
            </tr>
            <#list orderItems as orderItem>
                <tr>
                    <td>${orderItem.item.description}</td>
                    <td>
                        <div>
                            ${orderItem.qr}
                        </div>
                        ${orderItem.item.hostId}
                    </td>
                    <td>${orderItem.item.location}</td>
                </tr>
            </#list>
        </table>
    </div>
</div>
