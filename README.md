This mod adds a series of features and tools that enhance the Create mod experience.

### **Scrap Bucket**

Available in Andesite and Brass variants.

The Andesite Scrap Bucket destroys any items or fluids inserted into it via logistics components.

The Brass Scrap Bucket additionally extracts EXP Ingots from destroyed items and fluids at a low efficiency. EXP Ingots can be retrieved by sneaking and right-clicking empty-handed, or extracted via logistics components.

Items or filters can be placed in the side filter slot of the Brass Scrap Bucket, or configured via its GUI, to restrict insertion by filter.

Furthermore, when placed beneath a storage container (for either items or fluids), a target stock level for a specific item or fluid can be set in the GUI. When the filtered item's stock (or total stock if no filter is set) in the container above exceeds this value, the excess will be automatically inserted into the Scrap Bucket and destroyed.

![Andesite and Brass Scrap Bucket](https://media.forgecdn.net/attachments/description/null/description_e19646b3-6d2f-432b-aef6-d24921cfefca.png)

### **Network Manager**

The Network Manager allows you to create and manage tags for existing logistics networks, and assign names and icons to these tags.

It also enables quick configuration of the network connections for placed logistics components using existing tags.

![image](https://media.forgecdn.net/attachments/description/null/description_1e25876a-0bc2-48eb-8c0c-6ea2c24cb21b.png)

### **Labeled Redstone Link**

The Labeled Redstone Link functions essentially the same as Create's Redstone Link, but uses text strings as frequencies.

Only Labeled Redstone Links with matching text frequencies can transmit redstone signals. Like the Wireless Redstone Link, frequencies can be copied and pasted using the Clipboard.

Additionally, similar to how prepending # to a logistics network address in the Clipboard allows quick network assignment, prepending @ to a frequency lets players quickly configure the Link's frequency, with shortcut suggestions provided during configuration.

![image](https://media.forgecdn.net/attachments/description/null/description_d0029be3-ada4-47d0-96d8-f436b3a376b2.png)

### **Batch Mechanical Crafter**

A Mechanical Crafter that supports batch crafting. Works the same as a regular Mechanical Crafter, but each slot can hold a full stack of items and crafts them simultaneously. When processing packages with crafting requests, it always needs to be paired with a Batch Repackager.

When processing packages that carry crafting requests, if the amount of materials in a package exceeds the quantity that can be used in a single batch craft, the package will remain in the packager to wait for the next batch of crafting.

![image](https://media.forgecdn.net/attachments/description/1580762/description_c11c5ca3-68f2-4845-8b96-ecb352ef61d3.png)

### **Batch Repackager**

Functions the same as a Repackager, processing packages from Batch Crafting requests into a format the Batch Mechanical Crafter can accept. Supports multiple package input/output when raw material quantity exceeds a single package capacity, and also supports packages where a single request contains multiple recipes.

For raw material packages carrying crafting requests, the Batch Repackager will always output packages containing integer multiples of the materials required for a single craft of the corresponding recipe.

To make this easier to understand, assume a request uses 128 Iron Ingots and 768 Planks to craft 128 Shields:

The Input: There are three raw material packages: one containing 2x64 Iron Ingots, one with 9x64 Planks, and one with 3x64 Planks.

The Output: In this scenario, the Repackager will output them as follows:

First Package: Contains 74 Iron Ingots (64+10) and 444 Planks (6x64+60). It fills the package as much as possible while ensuring the output always contains integer multiples of the required crafting materials.

Second Package: Contains 54 Iron Ingots and 324 Planks (5x64+4).

Multiple Recipes: In cases where a single request contains multiple recipes (typically when manually requested via the Storage Manager), it will process the recipes sequentially and output the required raw material packages for each specific recipe.

![image](https://media.forgecdn.net/attachments/description/1580762/description_30e28858-e242-4b65-9c77-adcb12e06cbf.png)

### **Template Panel**

Added a new type of Factory Gauge used to define a Template Chain for template production.

A valid Template Chain must have ordinary Factory Gauges as leaf nodes, and every Template Panel must have an address configured.

Template Panels are configured in the same way as Factory Gauges, and support demand‑mode requests and Mechanical Crafting, but cannot initiate requests on their own and must be used together with a Work Warehouse.

In a valid Template Chain, all items set on Template Panels can be used for template production.

After configuring a Template Chain, you can see available template productions in the Stock Keeper menu, and request template production if there is an available Work Warehouse.

![image](https://media.forgecdn.net/attachments/description/1580762/description_9a099f28-98af-431f-aaab-f760bb8df199.png)

### **Work Warehouse**

A block used for template crafting, with two placement modes: standalone or placed flush against a block. The Work Warehouse must be configured with an address for receiving materials. When placed flush against a storage block, its address should be set to that connected storage's address.

There must be at least one valid Work Warehouse in the logistics network to perform template production. The number of Work Warehouses determines how many template productions can run simultaneously.

A standalone Work Warehouse must be connected to a Packager (no Stock Link required) and will input/output materials and products through that Packager. A Work Warehouse placed flush against a storage block connected to the same logistics network will retrieve items directly from the connected storage and ship them via that storage's Packager, which is more convenient and efficient.

After a template request is made, the Work Warehouse will obtain all materials needed to complete the request at once, either via package requests or directly from the connected storage.

Then, it will sequentially send materials through the Packager attached to the Work Warehouse or the Packager of the connected storage to the address configured on the Template Panel for production.

When the corresponding products are detected in the connected storage or logistics network, they will be transferred directly or requested into the Work Warehouse.

This process repeats until the requested template item is fully produced. The Work Warehouse will then output both the main product and byproducts to the address set when the request was made.

Additionally, if the address set for the request is the routing address of the connected storage (configurable, default is /back), the final product will be output to the connected storage instead of being sent via Packager.

![image](https://media.forgecdn.net/attachments/description/1580762/description_cf0cd4bc-0889-4064-aa50-15f10d3890e9.png)

### **Process Manager**

A panel used to monitor template crafting progress. It shows all ongoing template productions, and you can click to enter the detail interface to view detailed logs.

It also keeps up to 10 (configurable) history logs of completed or interrupted template requests, viewable in the history log interface.

Additionally, you can interrupt an ongoing request from the log detail interface of that template production.

After interruption, the Work Warehouse will send all materials currently stored in it to the address set for the request and refuse any further incoming materials.

![image](https://media.forgecdn.net/attachments/description/1580762/description_06a11e23-1265-4722-9432-f23227326f16.png)

### **Redstone Link Router**

The Redstone Link Router allows players to arbitrarily add and connect Wireless Redstone Link frequencies within its interface, including the item frequencies of vanilla Create Wireless Redstone Links and the text frequencies added by this mod.
When two frequencies are connected, the frequency on the left transmits its redstone signal to the frequency on the right. It also supports connecting a single frequency to multiple frequencies, allowing one frequency to control multiple others, and vice versa.

On top of that, players are allowed to use basic Logic Gates to control the transmission of redstone signals between frequencies. The add-frequency window supports adding AndGate and OrGate. Meanwhile, left-clicking a connection line between frequencies allows you to configure a NOT gate on the input end of that connection.

In this way, you can also control Labeled Redstone Links with the vanilla Create Redstone Remote.

![image](https://media.forgecdn.net/attachments/description/1580762/description_292534ab-6b55-4e6a-907c-81df192fdacb.png)

### **Packager Outbound Address Filtering**

In vanilla Create, when requesting items from a storage that is connected to multiple Packagers on the same network, one of those Packagers is chosen at random to ship the items.

This mod adds a feature that allows players to place Signs on Packagers that have a Stock Link attached. When requesting items from that storage, the shipment will be sent through the Packager whose Sign matches the request address.

Furthermore, similar to the package filter in vanilla Create, the text on the Sign supports Glob syntax for matching. For example, "?23" can match addresses like 123, 223, or 323.

If no Packager with a matching address is found, it will attempt to output through a Packager that has no Sign. If there is no such Sign‑less Packager either, it will pick a Packager at random.

![image](https://media.forgecdn.net/attachments/description/1580762/description_fd7d6de9-2105-4731-9bba-56aaf863c806.png)

### **Factory Panel Demand Request Mode**

A new button has been added to the recipe mode of the Factory Panel to toggle Demand Request Mode on or off for that panel. This button is only visible when the panel is connected to other Factory Panels.

In Demand Request Mode, the recipe configured in the panel is treated as a single-craft recipe, which utilizes the minimum amount of materials required to satisfy the crafting conditions.

If the monitored materials for the panel fall below the set threshold, it will attempt to request all raw materials needed to fill the material gap to the specified address at once, based on the configured minimum recipe.

Building on this, if there is an insufficient supply of raw materials in the network, the panel will attempt to send out the maximum possible batches of raw materials to fill the gap as much as it can.

To make this easier to understand, assume the panel is set to produce 1 Stripped Oak Log using 1 Oak Log, Demand Request Mode is enabled, and the monitored quantity for Stripped Oak Logs is configured to 64:

When the amount of Stripped Oak Logs in the logistics network is 20, the panel will attempt to request 44 Oak Logs to the specified address at once, based on the set minimum recipe.

If there are only 30 Oak Logs left in the network, it will request all 30 Oak Logs to the specified address at once, and the promised amount will correspondingly be exactly 30.

![image](https://media.forgecdn.net/attachments/description/1580762/description_0c271351-14a6-4930-bba4-1004c76a3706.png)

### **Quick Unpacking**

Allows players to right-click Packages inside inventory or any container GUI to quickly unpack them.

The contents will first fill the current container or inventory, then the hotbar. If there is insufficient space, items will be dropped at the player's feet.

### **Compatibilities**
Added compatibility with [Create: Fluid Logistics](https://modrinth.com/mod/createfluidlogistic), enabling fluids to participate in template production and allowing players to create Fluid Templates. Work Warehouses can receive and send fluid packages via their attached Packager or the Packager of the connected storage, without requiring an additional Fluid Packager. The outgoing address filter for Packagers also applies to Fluid Packagers. Scrap Buckets can now use a Fluid Packager to unpack fluids and destroy them.The hand pointer from Create: Fluid Logistics can be used on the Batch Mechanical Crafter.

Added compatibility with [Create: Phantom](https://modrinth.com/mod/createphantom), allowing the tunable portable ticker from Phantom Logistics to see and request templates. Fluid Templates are also supported.

Added compatibility with [Create: Extra Gauges](https://modrinth.com/mod/extra-gauges), enabling Template Panels to configure Mechanical Crafting recipes larger than 3x3 and participate normally in template production.

Added compatibility with [Create: Additional Logistics](https://modrinth.com/mod/create-additional-logistics), ensuring that the Demand Request Mode of Factory Panels is properly limited by the promise cap added by Additional Logistics.

It has been confirmed that, due to the new version of [Create: Mobile Packages](https://modrinth.com/mod/create-mobile-packages) being incompatible with the latest version 1.2.5 of [Create: Fluid Logistics](https://modrinth.com/mod/createfluidlogistic), and since this mod's compatibility with Fluid Logistics is based on that version, it will not be compatible with Mobile Packages until it updates.

It has been confirmed that, due to Mixin architecture conflicts, this mod will not be compatible with [Create: Factory Logistics](https://modrinth.com/mod/create_factory_logistics) in the short term.