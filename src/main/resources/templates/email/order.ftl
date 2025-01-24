<head>
    <title>WLS Bestillingsbekreftelse</title>
    <style>
        #right {
            display: flex;
            flex-direction: column;
        }

        .wrapper-class {
            display: flex;
            flex-direction: column;
        }

        .flexing {
            display: flex;
            flex-direction: row;
            justify-content: space-between;
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
    <div class="flexing">
        <div id="left">
            <div>
                En ny bestilling har blitt mottatt fra ${order.hostName}.
            </div>
            <div>
                Type: ${order.orderType}
            </div>
            <div>
                Kontaktperson: ${order.contactPerson}
            </div>
            <div>
                Melding fra bestiller: ${order.note}
            </div>
            <div>
                Er følgende felt viktige:

                <ol>
                    <li>Leveringsaddresse?</li>
                    <li>Internt/Eksternt?</li>
                </ol>
            </div>
        </div>
        <div id="right">
            <div>
                ${orderQrCode}
            </div>

            Lån: ${order.hostOrderId}
        </div>
        <!-- Yes, this abuses flexbox. You can thank Outlook for that -->
        <div id="dummy"></div>
        <div id="dummy2"></div>
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
