# UnderLX [![Discord](https://img.shields.io/discord/334423823552217090.svg)](https://perturbacoes.pt/discord?utm_source=gh-underlx) [![license](https://img.shields.io/github/license/underlx/underlx.svg)](https://github.com/underlx/underlx/blob/master/LICENSE)
This is an app for Android devices that shows information about the [Lisbon Metro](http://www.metrolisboa.pt/), as collected by our other project, [disturbancesmlx](https://github.com/underlx/disturbancesmlx).

The long-term goal is to become **the only Lisbon Metro app users will ever need**, and perhaps most importantly, **the one they'll want to have**.
The plan is for the app/service to feature a strong collaborative component, with users collectively submitting information about the current network status, through both automated and manual means - more or less like a **"Waze for the Lisbon Metro"**.

Integration with other public transit systems in Lisbon is being considered, and expansion to other transit networks, in other cities, is not out of the equation.

Integration with existing public transit open protocols is also being studied, but is not a priority. In this early stage of development, we want maximum freedom, to be able to implement any features in the way they best suit our needs for this specific network. Providing compatibility with existing protocols would probably be too much of a burden at this point.

Both the app and the server backends are being designed with support for multiple transit networks in mind. However, certain components such as the UI and the location services would need to be adjusted to the reality of each network.

<p float="left">
<img style="padding-bottom:0px" src="https://user-images.githubusercontent.com/29508939/74608782-ee0b4f80-50db-11ea-96ad-e204d489721e.png" width="32%">
<img style="padding-bottom:0px" src="https://user-images.githubusercontent.com/29508939/74608814-2d39a080-50dc-11ea-9bc9-89b2ffec790e.png" width="32%">
<img style="padding-bottom:0px" src="https://user-images.githubusercontent.com/29508939/74608832-43476100-50dc-11ea-9a98-c54d5c371cc4.png" width="32%">
</p>

## Installing and testing

The app is still in development, but is already published on Google Play.

[![UnderLX on Google Play](https://user-images.githubusercontent.com/984584/29083840-f2c43e7a-7c61-11e7-83ee-e6cbbe93f753.png)](https://play.google.com/store/apps/details?id=im.tny.segvault.disturbances&utm_source=github&utm_campaign=readme&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

By becoming our patron you'll be able to join the **beta-testing group**. You'll get access to earlier releases and exclusive content, and you'll help us support our operational costs.

[![Become a Patron!](https://c5.patreon.com/external/logo/become_a_patron_button@2x.png)](https://www.patreon.com/bePatron?u=10396324&utm_source=github)

## License

[Apache License 2.0](https://github.com/underlx/underlx/blob/master/LICENSE)

## Privacy considerations

This is a non-profit project. We don't plan on monetizing the app, not even inserting advertisements; this service is meant to monitor the subway network, not the users' lifes.

The collaborative features are being engineered with privacy in mind; users will not be uniquely identified. For example, when submitting information during trips, a random ID will be generated at the start of the trip, used for its duration, and destroyed once the user leaves the network.

## Disclaimer

We have no affiliation with _Metropolitano de Lisboa, E.P.E._. The code and the associated website are not sponsored or endorsed by them. We shall not be liable for any damages arising from the use of this code or associated websites.
