<head>
    <title>WLS Bestillingsbekreftelse</title>
    <style>
        .wrapper-class {
            display: flex;
            flex-direction: column;
        }

        .flexing {
            display: flex;
            flex-direction: row;
        }

        .flexed {
            margin: 0 0 0 40px;
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
    <div>
        En ny bestilling har blitt mottatt fra ${order.hostName}.
    </div>
    <br>
    <div class="flexing">
        <div>
            Lån: ${order.hostOrderId}
        </div>
        <div class="flexed">
            QR/STREKKODE HER
        </div>
    </div>
    <div>
        Type: ${order.orderType}<br>
    </div>
    <div>
        Kontaktperson: ${order.contactPerson}<br>
    </div>
    <div>
        Melding fra bestiller: ${order.note}<br>
    </div>
    <br>
    <div>
        Er følgende felt viktige:

        <ol>
            <li>Leveringsaddresse?</li>
            <li>Internt/Eksternt?</li>
        </ol>
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
                    <td>${orderItem.description}</td>
                    <td>
                        <div>
                            QR/STREKKODE HER
                        </div>
                        <br>
                        ${orderItem.hostId}
                    </td>
                    <td>${orderItem.location}</td>
                </tr>
            </#list>
        </table>
    </div>
</div>
