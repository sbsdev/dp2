# Daisyproducer2

Daisy Producer is a system to help you manage the production process
of accessible media. It assumes that you are transforming the content
from hard copy or any electronic format to [DTBook XML][1]. Once you
have the DTBook XML the [Daisy Pipeline][2] and [liblouis][3] are used
to generate Daisy Talking Books, Large Print and Braille in a fully
automated way.

[1]: https://en.wikipedia.org/wiki/DTBook
[2]: http://www.daisy.org/projects/pipeline/
[3]: http://www.liblouis.org

Daisy producer mainly tracks the status of the transformation process
and helps you manage your artifacts that are needed in the
transformation process.

For the generation of accessible media Daisy Producer offers a nice
interface however the work is essentially done by the Daisy Pipeline
and liblouis.

Daisyproducer2 is in essence a rewrite of the original [Daisyproducer][4]
using modern and future proof technology. The old system was written
in Python2 which is no longer supported.

[4]: https://github.com/sbsdev/daisyproducer

It consists of a [REST API][5] and an SPA that interacts with it.

[5]: http://localhost:3000/swagger-ui/index.html

The app is based on the [Luminus template][6] version "3.76" and was
built with the following command:

    lein new luminus dp2 +reitit +mysql +re-frame +swagger +auth

[6]: https://github.com/luminus-framework/luminus-template

## Prerequisites

You will need [Leiningen][7] 2.0 or above installed.

[7]: https://github.com/technomancy/leiningen

## Building

    lein uberjar

## Running

To start a web server for the application, run:

    java -Dconf=dev-config.edn -jar target/uberjar/daisyproducer2.jar

## License

[GNU Affero General Public License v3.0][8]

[8]: https://www.gnu.org/licenses/agpl-3.0.en.html
