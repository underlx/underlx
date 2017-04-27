# underlx
This is an app for Android devices that shows information about the [Lisbon Metro](http://www.metrolisboa.pt/), as collected by my other project, [disturbancesmlx](https://github.com/gbl08ma/disturbancesmlx).

The long-term goal is to become **the only Lisbon Metro app users will ever need**, and perhaps most importantly, **the one they'll want to have**.
The plan is for the app/service to feature a strong collaborative component, with users collectively submitting information about the current network status, through both automated and manual means - more or less like a **"Waze for the Lisbon Metro"**.

Integration with other public transit systems in Lisbon is being considered, and expansion to other transit networks, in other cities, is not out of the equation.

Integration with existing public transit open protocols is also being studied, but is not a priority. In this early stage of development, we want maximum freedom, to be able to implement any features in the way they best suit our needs for this specific network. Providing compatibility with existing protocols would probably be too much of a burden at this point.

Both the app and the server backends are being designed with support for multiple transit networks in mind. However, certain components such as the UI and the location services would need to be adjusted to the reality of each network.

![Home screen](https://cloud.githubusercontent.com/assets/984584/25486995/d1f93360-2b5a-11e7-9548-e77f5e7d3be7.png) 
![Route planning](https://cloud.githubusercontent.com/assets/984584/25487016/e2b3c350-2b5a-11e7-8c0b-3d8d49eea14f.png)

## Installing and testing

The app is still in an early development phase; don't expect much to work if at all.

The app is available on Google Play, for a "closed" beta testing group. To begin testing:

1. Start by joining [this Google group](https://groups.google.com/forum/#!forum/disturbancesmlx) with the same Google account you use on Google Play.
1. Enter the testing program by [going here](https://play.google.com/apps/testing/im.tny.segvault.disturbances/).
1. You should now be able to download the app from Google Play. Search for "UnderLX".

## License

[Apache License 2.0](https://github.com/gbl08ma/underlx/blob/master/LICENSE)

## Privacy considerations

This is a non-profit project. I don't plan on monetizing the app, not even inserting advertisements; this service is meant to monitor the subway network, not the users' lifes.

The collaborative features are being engineered with privacy in mind; users will not be uniquely identified. For example, when submitting information during trips, a random ID will be generated at the start of the trip, used for its duration, and destroyed once the user leaves the network.

Maintaining the servers for the online components incurs costs that are paid out of my own pocket and eventually any proceedings from my for-profit (but not profitable) projects such as [tny.im](https://tny.im).

## Disclaimer

I have no affiliation with _Metropolitano de Lisboa, E.P.E._. The code and the associated website are not sponsored or endorsed by them. I shall not be liable for any damages arising from the use of this code or associated website.
