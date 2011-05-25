/*
 * Copyright (c) 2011, Willow Garage, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Willow Garage, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
#include <ecto/ecto.hpp>
#include <iostream>
#include <boost/format.hpp>
#include <boost/foreach.hpp>

using ecto::tendrils;
namespace buster
{

struct FooPOD
{
  int x;
  float y;
};

struct FooPODModule
{
  static void declare_params(tendrils& parameters)
  {
    parameters.declare<std::string> ("str", "I print this:", "Hello World");
  }

  static void declare_io(const ecto::tendrils& parameters, ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    inputs.declare<FooPOD> ("foo", "A string to print");
    outputs.declare<FooPOD> ("foo", "A string to print");
  }

  int process(const ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    std::cout << inputs.get<FooPOD> ("foo").x << std::endl;
    outputs.get<FooPOD> ("foo").y = 3.14;
    return ecto::eOK;
  }

};

struct Printer
{
  static void declare_params(tendrils& parameters)
  {
    parameters.declare<std::string> ("str", "I print this:", "Hello World");
  }

  static void declare_io(const ecto::tendrils& parameters, ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    inputs.declare<std::string> ("str", "A string to print", "hello");

  }

  int process(const ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    std::cout << inputs.get<std::string> ("str") << std::endl;
    return ecto::eOK;
  }
};

struct Generate
{
  int step_;

  static void declare_params(tendrils& parameters)
  {
    parameters.declare<double> ("step", "The step with which i generate integers.", 2);
    parameters.declare<double> ("start", "My starting value", 0);
  }

  static void declare_io(const ecto::tendrils& parameters, ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    outputs.declare<double> ("out", "output", parameters.get<double> ("start") - parameters.get<double> ("step"));
  }

  void configure(tendrils& parameters)
  {
    step_ = parameters.get<double> ("step");
  }

  int process(const ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    outputs.get<double> ("out") += step_;
    return 0;
  }
};

struct Quitter
{
  static void declare_params(tendrils& params)
  {
    params.declare<std::string> ("str", "The default string to print", "EXIT");
  }

  static void declare_io(const tendrils& params, tendrils& in, tendrils& out)
  {
    in.declare<std::string> ("str", "The input string to listen to.", "");
  }

  void configure(tendrils& parms)
  {
    stop_word_ = parms.get<std::string> ("str");
  }

  int process(const tendrils& in, tendrils& /*out*/)
  {
    if (in.get<std::string> ("str") == stop_word_)
      return ecto::eQUIT;
    return ecto::eOK;
  }
  std::string stop_word_;
};

struct Multiply
{
  double factor_;

  static void declare_params(ecto::tendrils& p)
  {
    p.declare<double> ("factor", "A factor to multiply by.", 3.14);
  }

  static void declare_io(const ecto::tendrils& parameters, ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    inputs.declare<double> ("in", "multly in by factor");
    outputs.declare<double> ("out", "the result of in * factor");
  }

  void configure(tendrils& parms)
  {
    factor_ = parms.get<double> ("factor");
  }

  int process(const ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    outputs.get<double> ("out") = inputs.get<double> ("in") * factor_;
    return ecto::eOK;
  }

};
struct SharedPass
{

  typedef boost::shared_ptr<int> ptr_t;

  static void declare_params(ecto::tendrils& p)
  {
    p.declare<int> ("x", "Default value", -1);
  }

  static void declare_io(const ecto::tendrils& parameters, ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    inputs.declare<ptr_t> ("input", "a pass through", ptr_t(new int(parameters.get<int> ("x"))));

    outputs.declare<ptr_t> ("output", "a pass through", ptr_t(new int(-1)));

    outputs.declare<int> ("value", "value", -1);
  }

  int process(const ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    outputs.get<ptr_t> ("output") = inputs.get<ptr_t> ("input");
    outputs.get<int> ("value") = *outputs.get<ptr_t> ("output");
    return ecto::eOK;
  }
};
struct Scatter
{
  static void declare_params(ecto::tendrils& p)
  {
    p.declare<int> ("n", "Number to scatter...", 2);
    p.declare<int> ("x", "The value to scatter...", 13);
  }

  static void declare_io(const ecto::tendrils& parameters, ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    int n = parameters.get<int> ("n");
    for (int i = 0; i < n; i++)
      {
        outputs.declare<int> (str(boost::format("out_%04d") % i), str(boost::format("The %dth scatter") % i));
      }
  }

  void configure(tendrils& parameters)
  {
    n_ = parameters.get<int> ("n");
    x_ = parameters.get<int> ("x");
  }

  int process(const ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    for (int i = 0; i < n_; i++)
      {
        outputs.get<int> (str(boost::format("out_%04d") % i)) = x_;
      }
    return ecto::eOK;
  }

  int n_, x_;
};

template<typename ValueT>
struct Gather
{
  typedef ValueT value_type;

  static void declare_params(ecto::tendrils& p)
  {
    p.declare<int> ("n", "N to gather", 2);
  }

  static void declare_io(const ecto::tendrils& parameters, ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    int n = parameters.get<int> ("n");
    for (int ii = 0; ii < n; ii++)
      {
        inputs.declare<value_type> (str(boost::format("in_%04d") % ii), "An " + ecto::name_of<value_type>() + "input.");
      }
    outputs.declare<value_type> ("out", "The sum of all inputs.");
  }

  void configure(tendrils& parameters)
  {
    n_ = parameters.get<int> ("n");
  }

  int process(const ecto::tendrils& inputs, ecto::tendrils& outputs)
  {
    //SHOW();
    value_type& out = outputs.get<value_type> ("out");
    out = 0;
    typedef std::pair<std::string, ecto::tendril> pp;
    BOOST_FOREACH(const pp& in,inputs)
            {
              out += in.second.get<value_type> ();
            }
    return ecto::eOK;
  }

  int n_;
};

boost::shared_ptr<ecto::tendril> makePodTendril()
{
  boost::shared_ptr<ecto::tendril> p;
  ecto::tendril* t = new ecto::tendril(0, "doc");
  p.reset(t);
  return p;
}

}

BOOST_PYTHON_MODULE(buster)
{
  using namespace buster;
  ecto::wrap<Printer>("Printer", "A printer...");
  ecto::wrap<Generate>("Generate", "A generator module.");
  ecto::wrap<SharedPass>("SharedPass", "A shared pointer pass through");
  ecto::wrap<Multiply>("Multiply", "Multiply an input with a constant");
  ecto::wrap<Scatter>("Scatter", "Scatter a value...");
  ecto::wrap<Gather<int> >("Gather", "Gather a scattered value...");
  ecto::wrap<Gather<double> >("Gather_double", "Gather a scattered value...");
  ecto::wrap<Quitter>("Quitter", "Will quit the graph on an appropriate input.");
  boost::python::def("make_pod_tendril", buster::makePodTendril);
}
