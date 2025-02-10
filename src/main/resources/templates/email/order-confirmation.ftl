<head>
    <title>WLS Bestillingsbekreftelse</title>
    <style>
        dt {
            font-weight: bold;
        }
        dl, dd {
            font-size: 0.9rem;
        }
        dd {
            margin-bottom: 1em;
        }
    </style>
</head>

<div>
    <p>
        Bestillingen din har blitt mottatt.
    </p>
        <dl>
            <dt>Bestillingstype:</dt>
            <dd>${ordertype}</dd>
            <dt>Bestiller:</dt>
            <dd>${order.contactPerson}</dd>
            <dt>Melding fra bestiller:</dt>
            <dd>${order.note}</dd>
            <dt>Referansenummer:</dt>
            <dd>${order.hostOrderId}</dd>
        </dl>
    <h4>Ordrelinjer</h4>
    <ol>
        <#list order.orderLine as orderItem>
            <li>
                ${orderItem.hostId}
            </li>
        </#list>
    </ol>
</div>
