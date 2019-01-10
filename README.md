# underlx [![Discord](https://img.shields.io/discord/334423823552217090.svg)](https://perturbacoes.pt/discord?utm_source=gh-underlx) [![license](https://img.shields.io/github/license/underlx/underlx.svg)](https://github.com/underlx/underlx/blob/master/LICENSE)
This is an app for Android devices that shows information about the [Lisbon Metro](http://www.metrolisboa.pt/), as collected by our other project, [disturbancesmlx](https://github.com/underlx/disturbancesmlx).

The long-term goal is to become **the only Lisbon Metro app users will ever need**, and perhaps most importantly, **the one they'll want to have**.
The plan is for the app/service to feature a strong collaborative component, with users collectively submitting information about the current network status, through both automated and manual means - more or less like a **"Waze for the Lisbon Metro"**.

Integration with other public transit systems in Lisbon is being considered, and expansion to other transit networks, in other cities, is not out of the equation.

Integration with existing public transit open protocols is also being studied, but is not a priority. In this early stage of development, we want maximum freedom, to be able to implement any features in the way they best suit our needs for this specific network. Providing compatibility with existing protocols would probably be too much of a burden at this point.

Both the app and the server backends are being designed with support for multiple transit networks in mind. However, certain components such as the UI and the location services would need to be adjusted to the reality of each network.

![Home screen](https://user-images.githubusercontent.com/984584/29083578-1c558d1c-7c61-11e7-950a-85601eee0139.png) 
![Station info](https://user-images.githubusercontent.com/984584/29083600-32676e90-7c61-11e7-9b38-b4115f1f6299.png)
![Route planning](https://user-images.githubusercontent.com/984584/29083613-3a81046a-7c61-11e7-846f-4a4d33665350.png)

## Installing and testing

The app is still in development, but is already published on Google Play.

[![UnderLX on Google Play](https://user-images.githubusercontent.com/984584/29083840-f2c43e7a-7c61-11e7-83ee-e6cbbe93f753.png)](https://play.google.com/store/apps/details?id=im.tny.segvault.disturbances&utm_source=github&utm_campaign=readme&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

You can also join the **beta-testing group**. Beta testers might get releases earlier or more often:

1. Start by joining [this Google group](https://groups.google.com/forum/#!forum/disturbancesmlx) with the same Google account you use on Google Play.
1. Enter the testing program by [going here](https://play.google.com/apps/testing/im.tny.segvault.disturbances/).
1. You should now be able to download the app from Google Play. Search for "UnderLX".

## License

[Apache License 2.0](https://github.com/underlx/underlx/blob/master/LICENSE)

## Privacy considerations

This is a non-profit project. We don't plan on monetizing the app, not even inserting advertisements; this service is meant to monitor the subway network, not the users' lifes.

The collaborative features are being engineered with privacy in mind; users will not be uniquely identified. For example, when submitting information during trips, a random ID will be generated at the start of the trip, used for its duration, and destroyed once the user leaves the network.

Maintaining the servers for the online components incurs costs that are paid out of our own pockets and eventually any proceedings from gbl08ma's for-profit (but not profitable) projects such as [tny.im](https://tny.im).

## Disclaimer

We have no affiliation with _Metropolitano de Lisboa, E.P.E._. The code and the associated website are not sponsored or endorsed by them. We shall not be liable for any damages arising from the use of this code or associated website.
